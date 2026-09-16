package ru.shatrev.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import ru.shatrev.gateway.config.GatewayProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtValidationFilterTest {
    private final JwtValidationFilter filter = new JwtValidationFilter(new GatewayProperties(
            new GatewayProperties.Jwt("http://localhost:9999/jwks", "test"), List.of("/api/v1/auth/**")));

    @Test
    void authKeepsBearerButDropsForgedInternalHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/auth/me")
                .header("Authorization", "Bearer token").header("X-Internal-User-Id", "forged"));
        GatewayFilterChain chain = forwarded -> {
            assertEquals("Bearer token", forwarded.getRequest().getHeaders().getFirst("Authorization"));
            assertNull(forwarded.getRequest().getHeaders().getFirst("X-Internal-User-Id"));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
    }

    @Test
    void protectedRouteWithoutBearerIsUnauthorized() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/branches"));
        filter.filter(exchange, forwarded -> { fail("must not reach tasks"); return Mono.empty(); }).block();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void preflightNeedsNoBearerAndNeverReachesTasks() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.options("/api/v1/branches")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET"));
        filter.filter(exchange, forwarded -> { fail("must not reach tasks"); return Mono.empty(); }).block();
        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
    }
}
