package ru.shatrev.auth.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.shatrev.auth.service.EmailService;
import ru.shatrev.auth.service.GoogleOAuth2Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** Общая инфраструктура интеграционных тестов: реальная БД (Testcontainers) + реальные миграции Flyway. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractAuthIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    public static final String PASSWORD = "SecurePass1";
    public static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEY_PAIR = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @LocalServerPort
    protected int port;

    protected static HttpTestClient client;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected EmailService emailService;

    @MockitoBean
    protected GoogleOAuth2Service googleOAuth2Service;

    protected HttpTestClient http() {
        if (client == null) {
            client = new HttpTestClient("http://localhost:" + port);
        }
        return client;
    }

    @DynamicPropertySource
    static void authProperties(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.kid", () -> "test-kid");
        registry.add("auth.jwt.private-key", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
        registry.add("auth.jwt.public-key", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
        registry.add("auth.jwt.issuer", () -> "shatrev-auth");
        registry.add("auth.cors.allowed-origins", () -> ALLOWED_ORIGIN);
    }

    // ---------- HTTP-помощники ----------

    protected HttpTestClient.SimpleResponse post(String path, Object body, Map<String, String> headers) {
        return http().postJson(path, body, headers);
    }

    protected HttpTestClient.SimpleResponse postEmpty(String path, Map<String, String> headers) {
        return http().post(path, headers);
    }

    protected HttpTestClient.SimpleResponse get(String path, Map<String, String> headers) {
        return http().get(path, headers);
    }

    protected Map<String, String> headersWithIp(String ip) {
        return Map.of("X-Forwarded-For", ip);
    }

    protected Map<String, String> bearer(String accessToken) {
        return Map.of("Authorization", "Bearer " + accessToken);
    }

    protected Map<String, String> cookie(Map<String, String> base, String setCookieValue) {
        var headers = new java.util.HashMap<>(base);
        int end = setCookieValue.indexOf(';');
        headers.put("Cookie", end < 0 ? setCookieValue : setCookieValue.substring(0, end));
        return headers;
    }

    /** Код ошибки из тела в едином формате (раздел 4.1). */
    protected String errorCode(HttpTestClient.SimpleResponse response) {
        Object error = http().json(response).get("error");
        return error instanceof Map<?, ?> map ? String.valueOf(map.get("code")) : null;
    }

    // ---------- Сценарные помощники ----------

    protected HttpTestClient.SimpleResponse register(String email, String password, String ip) {
        return post("/api/v1/auth/register", Map.of("email", email, "password", password), headersWithIp(ip));
    }

    /** Регистрирует пользователя и возвращает токен подтверждения из письма-заглушки. */
    protected String registerAndCaptureToken(String email, String ip) {
        register(email, PASSWORD, ip);
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendConfirmationEmail(anyString(), captor.capture());
        return captor.getValue();
    }

    protected HttpTestClient.SimpleResponse login(String email, String password, String ip) {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password), headersWithIp(ip));
    }

    protected String accessTokenOf(HttpTestClient.SimpleResponse response) {
        var json = http().json(response);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) json;
        return String.valueOf(body.get("accessToken"));
    }

    private static String pem(String marker, byte[] encoded) {
        return "-----BEGIN " + marker + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + marker + "-----";
    }
}
