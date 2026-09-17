package ru.shatrev.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Signs the verified user for tasks after route path rewrites have run. */
@Component
public class TasksContextFilter implements GlobalFilter, Ordered {
    private final byte[] key;

    public TasksContextFilter(@Value("${gateway.internal.tasks-hmac-key}") String encodedKey) {
        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TASKS_INTERNAL_HMAC_KEY must be Base64", e);
        }
        if (key.length < 32) throw new IllegalStateException("TASKS_INTERNAL_HMAC_KEY must contain at least 32 bytes");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        UUID userId = exchange.getAttribute(JwtValidationFilter.USER_ID_ATTRIBUTE);
        String path = exchange.getRequest().getURI().getRawPath();
        if (userId == null || !(path.startsWith("/api/v1/branches") || path.startsWith("/api/v1/tasks")
                || path.startsWith("/api/v1/work-plans") || path.equals("/api/v1/navigation/tree"))) {
            return chain.filter(exchange);
        }
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String message = "v1\ntasks\n" + userId + "\n" + timestamp + "\n"
                + exchange.getRequest().getMethod().name() + "\n" + path;
        String signature = sign(message);
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("Authorization");
            headers.set("X-Internal-User-Id", userId.toString());
            headers.set("X-Internal-Auth-Time", timestamp);
            headers.set("X-Internal-Auth-Signature", signature);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private String sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign internal context", e);
        }
    }

    @Override
    public int getOrder() { return 10000; }
}
