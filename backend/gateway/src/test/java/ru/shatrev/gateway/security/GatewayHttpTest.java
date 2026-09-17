package ru.shatrev.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.internal.tasks-hmac-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class GatewayHttpTest {
    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void preflightIsHandledBeforeJwtAndWithoutTasks() throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri("/api/v1/branches"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:5173", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @Test
    void protectedBranchRouteRejectsMissingJwt() throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri("/api/v1/branches")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("UNAUTHORIZED"));
    }

    @Test
    void workPlanPreflightAndProtectedRequestFollowSameRules() throws Exception {
        var preflight = http.send(HttpRequest.newBuilder(uri("/api/v1/work-plans/123"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, preflight.statusCode());
        assertEquals("http://localhost:5173", preflight.headers()
                .firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals(401, http.send(HttpRequest.newBuilder(uri("/api/v1/work-plans/123")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
