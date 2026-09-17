package ru.shatrev.tasks.workplan;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("isDockerAvailable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkPlanIntegrationTest {
    private static final byte[] KEY = new byte[32];
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("tasks.internal.hmac-key", () -> Base64.getEncoder().encodeToString(KEY));
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    public static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+.*");
        } catch (Exception e) {
            return false;
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void lifecycleOwnershipInputAndAudit() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID branch = createBranch(owner);
        String nested = "/api/v1/branches/" + branch + "/plans";
        assertEquals(400, send(owner, "POST", nested, "{\"name\":\"План\",\"userId\":\"forged\"}").statusCode());
        assertEquals(400, send(owner, "POST", nested, "{\"name\":123}").statusCode());
        assertEquals(400, send(owner, "POST", nested, "{\"name\":\"a\"}").statusCode());
        assertEquals(201, send(owner, "POST", nested, "{\"name\":\"" + "😀".repeat(150) + "\"}").statusCode());
        assertEquals(400, send(owner, "POST", nested, "{\"name\":\"" + "😀".repeat(151) + "\"}").statusCode());

        var created = send(owner, "POST", nested, "{\"name\":\" План \"}");
        assertEquals(201, created.statusCode());
        UUID plan = UUID.fromString(json.readTree(created.body()).get("id").asText());
        String path = "/api/v1/work-plans/" + plan;
        assertEquals(path, created.headers().firstValue("Location").orElseThrow());
        assertEquals(branch.toString(), json.readTree(send(owner, "GET", path, null).body()).get("branchId").asText());
        assertEquals(404, send(other, "GET", nested, null).statusCode());
        assertEquals(404, send(other, "POST", nested, "{\"name\":\"Чужой\"}").statusCode());
        assertEquals(404, send(other, "GET", path, null).statusCode());
        assertEquals(400, send(owner, "GET", "/api/v1/work-plans/wrong", null).statusCode());

        assertEquals(200, send(owner, "PUT", path, "{\"name\":\"Новый план\"}").statusCode());
        Timestamp updated = jdbc.queryForObject("SELECT updated_at FROM work_plans WHERE id = ?", Timestamp.class, plan);
        assertEquals(200, send(owner, "PUT", path, "{\"name\":\" Новый план \"}").statusCode());
        assertEquals(updated, jdbc.queryForObject("SELECT updated_at FROM work_plans WHERE id = ?", Timestamp.class, plan));
        assertEquals(204, send(owner, "POST", path + "/archive", null).statusCode());
        assertEquals(204, send(owner, "POST", path + "/archive", null).statusCode());
        assertEquals(404, send(owner, "GET", path, null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM work_plans WHERE id = ?", Integer.class, plan));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_type = 'work_plan' AND entity_id = ?",
                Integer.class, plan));
    }

    @Test
    void branchArchiveCascadesOnlyActivePlansOnce() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID branch = createBranch(owner);
        UUID active = createPlan(owner, branch, "Активный");
        UUID archived = createPlan(owner, branch, "Уже архивный");
        assertEquals(204, send(owner, "POST", "/api/v1/work-plans/" + archived + "/archive", null).statusCode());
        Timestamp earlier = jdbc.queryForObject("SELECT archived_at FROM work_plans WHERE id = ?", Timestamp.class, archived);

        assertEquals(204, send(owner, "POST", "/api/v1/branches/" + branch + "/archive", null).statusCode());
        Timestamp branchTime = jdbc.queryForObject("SELECT archived_at FROM branches WHERE id = ?", Timestamp.class, branch);
        assertEquals(branchTime, jdbc.queryForObject("SELECT archived_at FROM work_plans WHERE id = ?", Timestamp.class, active));
        assertEquals(earlier, jdbc.queryForObject("SELECT archived_at FROM work_plans WHERE id = ?", Timestamp.class, archived));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_events "
                + "WHERE entity_type = 'work_plan' AND entity_id = ? AND action = 'archived'", Integer.class, active));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_events "
                + "WHERE entity_type = 'work_plan' AND entity_id = ? AND action = 'archived'", Integer.class, archived));
        assertEquals(204, send(owner, "POST", "/api/v1/branches/" + branch + "/archive", null).statusCode());
        assertEquals(404, send(owner, "POST", "/api/v1/work-plans/" + archived + "/archive", null).statusCode());
        assertEquals(404, send(owner, "GET", "/api/v1/branches/" + branch + "/plans", null).statusCode());
    }

    @Test
    void paginationAndLiteralSearchAreBoundToBranchAndQuery() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID branch = createBranch(owner);
        UUID another = createBranch(owner);
        Instant start = Instant.parse("2026-09-17T10:00:00Z");
        for (int i = 0; i < 51; i++) {
            Timestamp time = Timestamp.from(start.plusSeconds(i));
            jdbc.update("INSERT INTO work_plans (id, branch_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), branch, "План " + i, time, time);
        }
        String nested = "/api/v1/branches/" + branch + "/plans";
        var first = json.readTree(send(owner, "GET", nested, null).body());
        assertEquals(50, first.get("items").size());
        String cursor = first.get("nextCursor").asText();
        assertEquals(1, json.readTree(send(owner, "GET", nested + "?cursor=" + cursor, null).body())
                .get("items").size());
        assertEquals(400, send(owner, "GET", "/api/v1/branches/" + another + "/plans?cursor=" + cursor, null).statusCode());
        assertEquals(400, send(owner, "GET", nested + "?q=Test&cursor=" + cursor, null).statusCode());

        createPlan(owner, branch, "100%_готово");
        for (String query : new String[]{"%", "_", "ГОТОВО"}) {
            String encoded = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
            var items = json.readTree(send(owner, "GET", nested + "?q=" + encoded, null).body()).get("items");
            assertEquals(1, items.size());
            assertEquals("100%_готово", items.get(0).get("name").asText());
        }
    }

    @Test
    void concurrentCreateAndBranchArchiveLeaveNoActiveChild() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID branch = createBranch(owner);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var create = pool.submit(() -> {
                start.await();
                return send(owner, "POST", "/api/v1/branches/" + branch + "/plans",
                        "{\"name\":\"Гонка\"}").statusCode();
            });
            var archive = pool.submit(() -> {
                start.await();
                return send(owner, "POST", "/api/v1/branches/" + branch + "/archive", null).statusCode();
            });
            start.countDown();
            int createStatus = create.get(15, TimeUnit.SECONDS);
            assertTrue(createStatus == 201 || createStatus == 404);
            assertEquals(204, archive.get(15, TimeUnit.SECONDS));
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM work_plans "
                + "WHERE branch_id = ? AND archived_at IS NULL", Integer.class, branch));
    }

    @Test
    void failedCascadeAuditRollsBackBranchAndPlans() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID branch = createBranch(owner);
        UUID plan = createPlan(owner, branch, "Откат");
        jdbc.execute("CREATE FUNCTION f5_reject_archive_event() RETURNS trigger AS $$ "
                + "BEGIN IF NEW.entity_type = 'work_plan' AND NEW.action = 'archived' THEN "
                + "RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER f5_reject_archive_event BEFORE INSERT ON audit_events "
                + "FOR EACH ROW EXECUTE FUNCTION f5_reject_archive_event()");
        try {
            assertEquals(500, send(owner, "POST", "/api/v1/branches/" + branch + "/archive", null).statusCode());
        } finally {
            jdbc.execute("DROP TRIGGER f5_reject_archive_event ON audit_events");
            jdbc.execute("DROP FUNCTION f5_reject_archive_event()");
        }
        assertNull(jdbc.queryForObject("SELECT archived_at FROM branches WHERE id = ?", Timestamp.class, branch));
        assertNull(jdbc.queryForObject("SELECT archived_at FROM work_plans WHERE id = ?", Timestamp.class, plan));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_events "
                + "WHERE entity_type = 'work_plan' AND entity_id = ? AND action = 'archived'", Integer.class, plan));
    }

    private UUID createBranch(UUID owner) throws Exception {
        var response = send(owner, "POST", "/api/v1/branches", "{\"name\":\"Ветка\"}");
        assertEquals(201, response.statusCode());
        return UUID.fromString(json.readTree(response.body()).get("id").asText());
    }

    private UUID createPlan(UUID owner, UUID branch, String name) throws Exception {
        var response = send(owner, "POST", "/api/v1/branches/" + branch + "/plans", "{\"name\":\"" + name + "\"}");
        assertEquals(201, response.statusCode());
        return UUID.fromString(json.readTree(response.body()).get("id").asText());
    }

    private HttpResponse<String> send(UUID owner, String method, String path, String body) throws Exception {
        String rawPath = path.split("\\?", 2)[0];
        String time = Long.toString(Instant.now().getEpochSecond());
        String payload = "v1\ntasks\n" + owner + "\n" + time + "\n" + method + "\n" + rawPath;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Internal-User-Id", owner.toString())
                .header("X-Internal-Auth-Time", time)
                .header("X-Internal-Auth-Signature", signature);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
