package ru.shatrev.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.internal.tasks-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class GatewayAccessIntegrationTest {
    private static final KeyPair KEYS;
    private static final HttpServer DOWNSTREAM;
    private static final AtomicReference<com.sun.net.httpserver.Headers> LAST_HEADERS = new AtomicReference<>();

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KEYS = generator.generateKeyPair();
            DOWNSTREAM = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RSAPublicKey publicKey = (RSAPublicKey) KEYS.getPublic();
            String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\""
                    + unsigned(publicKey.getModulus()) + "\",\"e\":\"" + unsigned(publicKey.getPublicExponent()) + "\"}]}";
            DOWNSTREAM.createContext("/jwks", exchange -> {
                byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            DOWNSTREAM.createContext("/api/v1/branches", exchange -> {
                LAST_HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "internal rejection".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            DOWNSTREAM.createContext("/api/v1/work-plans", exchange -> {
                LAST_HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "internal rejection".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            DOWNSTREAM.createContext("/api/v1/navigation/tree", exchange -> {
                LAST_HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "internal rejection".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            DOWNSTREAM.createContext("/api/v1/auth/me", exchange -> {
                LAST_HEADERS.set(exchange.getRequestHeaders());
                byte[] body = "auth".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            DOWNSTREAM.start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort();
        registry.add("gateway.jwt.jwks-uri", () -> url + "/jwks");
        registry.add("gateway.jwt.issuer", () -> "test-issuer");
        registry.add("TASKS_SERVICE_URL", () -> url);
        registry.add("AUTH_SERVICE_URL", () -> url);
    }

    @AfterAll
    static void stopDownstream() {
        DOWNSTREAM.stop(0);
    }

    @LocalServerPort
    int port;

    @Test
    void accessTokenYieldsMinimalContextAndInternal401Becomes502() throws Exception {
        UUID user = UUID.randomUUID();
        LAST_HEADERS.set(null);
        var response = call(token(user, "access"));
        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("UPSTREAM_UNAVAILABLE"), response.body());
        assertNotNull(LAST_HEADERS.get());
        assertEquals(user.toString(), LAST_HEADERS.get().getFirst("X-Internal-User-Id"));
        assertNotNull(LAST_HEADERS.get().getFirst("X-Internal-Auth-Signature"));
        assertNull(LAST_HEADERS.get().getFirst("Authorization"));
        String time = LAST_HEADERS.get().getFirst("X-Internal-Auth-Time");
        String message = "v1\ntasks\n" + user + "\n" + time + "\nGET\n/api/v1/branches";
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(new byte[32], "HmacSHA256"));
        assertEquals(Base64.getUrlEncoder().withoutPadding().encodeToString(
                        mac.doFinal(message.getBytes(StandardCharsets.UTF_8))),
                LAST_HEADERS.get().getFirst("X-Internal-Auth-Signature"));
    }

    @Test
    void refreshTokenIsRejectedBeforeDownstream() throws Exception {
        LAST_HEADERS.set(null);
        assertEquals(401, call(token(UUID.randomUUID(), "refresh")).statusCode());
        assertNull(LAST_HEADERS.get());
    }

    @Test
    void unknownJwksKeyIsRejectedBeforeDownstream() throws Exception {
        LAST_HEADERS.set(null);
        assertEquals(401, call(token(UUID.randomUUID(), "access", "unknown-kid")).statusCode());
        assertNull(LAST_HEADERS.get());
    }

    @Test
    void workPlanAndNestedRoutesCarryVerifiedContext() throws Exception {
        UUID user = UUID.randomUUID();
        for (String path : new String[]{"/api/v1/work-plans/" + UUID.randomUUID(),
                "/api/v1/branches/" + UUID.randomUUID() + "/plans", "/api/v1/navigation/tree"}) {
            LAST_HEADERS.set(null);
            var response = call(path, token(user, "access"));
            assertEquals(502, response.statusCode());
            assertTrue(response.body().contains("UPSTREAM_UNAVAILABLE"));
            assertEquals(user.toString(), LAST_HEADERS.get().getFirst("X-Internal-User-Id"));
            assertNotNull(LAST_HEADERS.get().getFirst("X-Internal-Auth-Signature"));
            assertNull(LAST_HEADERS.get().getFirst("Authorization"));
        }
        LAST_HEADERS.set(null);
        assertEquals(401, call("/api/v1/work-plans/" + UUID.randomUUID(),
                token(user, "refresh")).statusCode());
        assertNull(LAST_HEADERS.get());
    }

    @Test
    void authRouteKeepsBearerAndStripsClientInternalHeaders() throws Exception {
        LAST_HEADERS.set(null);
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/auth/me"))
                .header("Authorization", "Bearer auth-owned-token")
                .header("X-Internal-User-Id", "forged")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("Bearer auth-owned-token", LAST_HEADERS.get().getFirst("Authorization"));
        assertNull(LAST_HEADERS.get().getFirst("X-Internal-User-Id"));
    }

    private HttpResponse<String> call(String token) throws Exception {
        return call("/api/v1/branches", token);
    }

    private HttpResponse<String> call(String path, String token) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("X-Internal-User-Id", "forged")
                .header("X-Internal-Auth-Signature", "forged")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token(UUID user, String type) throws Exception {
        return token(user, type, "test");
    }

    private static String token(UUID user, String type, String kid) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer("test-issuer").subject(user.toString())
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("type", type).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
    }
}
