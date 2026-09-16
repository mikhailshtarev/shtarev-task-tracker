package ru.shatrev.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TasksContextFilterTest {
    private final TasksContextFilter filter = new TasksContextFilter(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void signsOnlyVerifiedUserAndDropsBearer() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/branches")
                .header("Authorization", "Bearer private-token"));
        UUID user = UUID.randomUUID();
        exchange.getAttributes().put(JwtValidationFilter.USER_ID_ATTRIBUTE, user);
        GatewayFilterChain chain = forwarded -> {
            assertNull(forwarded.getRequest().getHeaders().getFirst("Authorization"));
            assertEquals(user.toString(), forwarded.getRequest().getHeaders().getFirst("X-Internal-User-Id"));
            assertNotNull(forwarded.getRequest().getHeaders().getFirst("X-Internal-Auth-Signature"));
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
    }

    @Test
    void responseFilterConvertsTasksInternalUnauthorizedToPublicBadGateway() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/branches"));
        exchange.getAttributes().put(JwtValidationFilter.USER_ID_ATTRIBUTE, UUID.randomUUID());
        GatewayFilterChain chain = forwarded -> {
            forwarded.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            byte[] body = "private reason".getBytes(StandardCharsets.UTF_8);
            return forwarded.getResponse().writeWith(Mono.just(forwarded.getResponse().bufferFactory().wrap(body)));
        };
        new TasksUnauthorizedResponseFilter().filter(exchange, chain).block();
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getBodyAsString().block().contains("UPSTREAM_UNAVAILABLE"));
    }
}
