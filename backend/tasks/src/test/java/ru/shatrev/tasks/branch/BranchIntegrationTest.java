package ru.shatrev.tasks.branch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIf("isDockerAvailable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BranchIntegrationTest {
    private static final byte[] KEY = new byte[32];
    private static final String BASE = "/api/v1/branches";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

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

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public static boolean isDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info", "--format", "{{.ServerVersion}}")
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.exitValue() == 0 && version.matches("[0-9]+\\.[0-9]+\\.[0-9]+.*");
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void createRenameArchiveAndOwnershipAreTransactional() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var created = send(owner, "POST", BASE, "{\"name\":\"  Спорт  \"}");
        assertEquals(201, created.statusCode());
        String id = json.readTree(created.body()).get("id").asText();
        assertEquals("Спорт", json.readTree(created.body()).get("name").asText());
        assertEquals(BASE + "/" + id, created.headers().firstValue("Location").orElseThrow());
        assertEquals(404, send(other, "GET", BASE + "/" + id, null).statusCode());
        assertEquals(404, send(other, "POST", BASE + "/" + id + "/archive", null).statusCode());

        assertEquals(200, send(owner, "PUT", BASE + "/" + id, "{\"name\":\"Работа\"}").statusCode());
        assertEquals(200, send(owner, "PUT", BASE + "/" + id, "{\"name\":\"Работа\"}").statusCode());
        assertEquals(204, send(owner, "POST", BASE + "/" + id + "/archive", null).statusCode());
        assertEquals(204, send(owner, "POST", BASE + "/" + id + "/archive", null).statusCode());
        assertEquals(404, send(owner, "GET", BASE + "/" + id, null).statusCode());
        assertEquals(0, json.readTree(send(owner, "GET", BASE, null).body()).get("items").size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM branches WHERE id = ?", Integer.class, UUID.fromString(id)));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ?", Integer.class, UUID.fromString(id)));
    }

    @Test
    void rejectsUnknownFieldsAndBadIdentifiers() throws Exception {
        UUID owner = UUID.randomUUID();
        assertEquals(400, send(owner, "POST", BASE, "{\"name\":\"Спорт\",\"userId\":\"forged\"}").statusCode());
        assertEquals(400, send(owner, "POST", BASE, "{\"name\":123}").statusCode());
        assertEquals(400, send(owner, "GET", BASE + "/wrong", null).statusCode());
        assertEquals(400, send(owner, "GET", BASE + "?limit=51", null).statusCode());
        assertEquals(400, send(owner, "GET", BASE + "?cursor=broken", null).statusCode());
        assertEquals(400, send(owner, "POST", BASE, "{\"name\":\"😀\"}").statusCode());
        assertEquals(201, send(owner, "POST", BASE, "{\"name\":\"" + "😀".repeat(100) + "\"}").statusCode());
        assertEquals(400, send(owner, "POST", BASE, "{\"name\":\"" + "😀".repeat(101) + "\"}").statusCode());
    }

    @Test
    void keysetPaginationAndLiteralSearchAreOwnerScoped() throws Exception {
        UUID owner = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-16T10:00:00Z");
        for (int i = 0; i < 51; i++) {
            Instant time = start.plusSeconds(i);
            jdbc.update("INSERT INTO branches (id, user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), owner, "Ветка " + i, Timestamp.from(time), Timestamp.from(time));
        }
        var first = json.readTree(send(owner, "GET", BASE, null).body());
        assertEquals(50, first.get("items").size());
        String cursor = first.get("nextCursor").asText();
        var second = json.readTree(send(owner, "GET", BASE + "?cursor=" + cursor, null).body());
        assertEquals(1, second.get("items").size());
        assertTrue(second.get("nextCursor").isNull());
        assertEquals(400, send(owner, "GET", BASE + "?q=test&cursor=" + cursor, null).statusCode());

        assertEquals(201, send(owner, "POST", BASE, "{\"name\":\"100%_готово\"}").statusCode());
        for (String query : new String[]{"%", "_", "ГОТОВО"}) {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            var items = json.readTree(send(owner, "GET", BASE + "?q=" + encoded, null).body()).get("items");
            assertEquals(1, items.size());
            assertEquals("100%_готово", items.get(0).get("name").asText());
        }
    }

    @Test
    void nestedBranchesValidateParentDepthAndArchiveWholeSubtree() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID root = createdId(send(owner, "POST", BASE, "{\"name\":\"Корень\"}"));
        UUID foreign = createdId(send(other, "POST", BASE, "{\"name\":\"Чужой\"}"));
        assertEquals(404, send(owner, "POST", BASE, branchBody("Нет доступа", foreign)).statusCode());
        UUID current = root;
        UUID child = null;
        for (int depth = 2; depth <= 7; depth++) {
            var response = send(owner, "POST", BASE, branchBody("Уровень " + depth, current));
            assertEquals(201, response.statusCode(), response.body());
            assertEquals(depth, json.readTree(response.body()).get("depth").asInt());
            assertEquals(current.toString(), json.readTree(response.body()).get("parentId").asText());
            current = createdId(response);
            if (depth == 2) child = current;
        }
        assertEquals(400, send(owner, "POST", BASE, branchBody("Восьмой", current)).statusCode());
        assertEquals(400, send(owner, "PUT", BASE + "/" + root, branchBody("Перемещение", child)).statusCode());
        UUID plan = createdId(send(owner, "POST", BASE + "/" + child + "/plans", "{\"name\":\"План\"}"));
        assertEquals(204, send(owner, "POST", BASE + "/" + root + "/archive", null).statusCode());
        Timestamp archivedAt = jdbc.queryForObject("SELECT archived_at FROM branches WHERE id = ?", Timestamp.class, root);
        assertEquals(archivedAt, jdbc.queryForObject("SELECT archived_at FROM branches WHERE id = ?", Timestamp.class, current));
        assertEquals(archivedAt, jdbc.queryForObject("SELECT archived_at FROM work_plans WHERE id = ?", Timestamp.class, plan));
        assertEquals(404, send(owner, "POST", BASE, branchBody("Архивный", child)).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ? AND action = 'archived'",
                Integer.class, child));
    }

    @Test
    void navigationTreeLoadsOneLevelWithOwnerScopedCursor() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID root = createdId(send(owner, "POST", BASE, "{\"name\":\"Работа\"}"));
        UUID child = createdId(send(owner, "POST", BASE, branchBody("Клиент", root)));
        UUID plan = createdId(send(owner, "POST", BASE + "/" + root + "/plans", "{\"name\":\"План\"}"));
        createdId(send(other, "POST", BASE, "{\"name\":\"Чужой\"}"));
        String tree = "/api/v1/navigation/tree";
        var roots = json.readTree(send(owner, "GET", tree, null).body());
        assertEquals(1, roots.get("items").size());
        assertEquals(root.toString(), roots.get("items").get(0).get("id").asText());
        assertTrue(roots.get("items").get(0).get("hasChildren").asBoolean());
        var first = json.readTree(send(owner, "GET", tree + "?parentType=branch&parentId=" + root + "&limit=1", null).body());
        assertEquals(child.toString(), first.get("items").get(0).get("id").asText());
        String cursor = first.get("nextCursor").asText();
        var second = json.readTree(send(owner, "GET", tree + "?parentType=branch&parentId=" + root
                + "&limit=1&cursor=" + cursor, null).body());
        assertEquals(plan.toString(), second.get("items").get(0).get("id").asText());
        assertEquals("work_plan", second.get("items").get(0).get("type").asText());
        assertTrue(second.get("nextCursor").isNull());
        assertEquals(400, send(other, "GET", tree + "?parentType=branch&parentId=" + root
                + "&cursor=" + cursor, null).statusCode());
        assertEquals(404, send(other, "GET", tree + "?parentType=branch&parentId=" + root, null).statusCode());
        assertEquals(0, json.readTree(send(owner, "GET", tree + "?parentType=work_plan&parentId=" + plan, null)
                .body()).get("items").size());
        assertEquals(400, send(owner, "GET", tree + "?parentType=branch", null).statusCode());
        assertEquals(400, send(owner, "GET", tree + "?limit=501", null).statusCode());
    }

    @Test
    void failedDescendantAuditRollsBackEntireArchive() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID root = createdId(send(owner, "POST", BASE, "{\"name\":\"Корень\"}"));
        UUID child = createdId(send(owner, "POST", BASE, branchBody("Потомок", root)));
        UUID plan = createdId(send(owner, "POST", BASE + "/" + child + "/plans", "{\"name\":\"План\"}"));
        jdbc.execute("CREATE FUNCTION f51_reject_child_archive() RETURNS trigger AS $$ "
                + "BEGIN IF NEW.entity_type = 'branch' AND NEW.entity_id = '" + child + "' "
                + "AND NEW.action = 'archived' THEN RAISE EXCEPTION 'test audit failure'; END IF; "
                + "RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER f51_reject_child_archive BEFORE INSERT ON audit_events "
                + "FOR EACH ROW EXECUTE FUNCTION f51_reject_child_archive()");
        try {
            assertEquals(500, send(owner, "POST", BASE + "/" + root + "/archive", null).statusCode());
        } finally {
            jdbc.execute("DROP TRIGGER f51_reject_child_archive ON audit_events");
            jdbc.execute("DROP FUNCTION f51_reject_child_archive()");
        }
        assertNull(jdbc.queryForObject("SELECT archived_at FROM branches WHERE id = ?", Timestamp.class, root));
        assertNull(jdbc.queryForObject("SELECT archived_at FROM branches WHERE id = ?", Timestamp.class, child));
        assertNull(jdbc.queryForObject("SELECT archived_at FROM work_plans WHERE id = ?", Timestamp.class, plan));
    }

    private UUID createdId(HttpResponse<String> response) throws Exception {
        assertEquals(201, response.statusCode(), response.body());
        return UUID.fromString(json.readTree(response.body()).get("id").asText());
    }

    private static String branchBody(String name, UUID parentId) {
        return "{\"name\":\"" + name + "\",\"parentId\":\"" + parentId + "\"}";
    }

    private HttpResponse<String> send(UUID owner, String method, String path, String body) throws Exception {
        String rawPath = path.split("\\?", 2)[0];
        String time = Long.toString(Instant.now().getEpochSecond());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
        String payload = "v1\ntasks\n" + owner + "\n" + time + "\n" + method + "\n" + rawPath;
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
