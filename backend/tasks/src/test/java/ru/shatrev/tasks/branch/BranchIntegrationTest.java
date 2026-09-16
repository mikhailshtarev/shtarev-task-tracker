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
            return process.exitValue() == 0;
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
