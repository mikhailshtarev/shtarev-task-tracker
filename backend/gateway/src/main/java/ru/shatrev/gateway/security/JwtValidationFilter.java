package ru.shatrev.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.shatrev.gateway.config.GatewayProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Валидация Bearer access-токена для защищённых маршрутов по JWKS auth-сервиса (раздел 6.7).
 * /api/v1/auth/** не проверяется здесь — сервис auth применяет собственные правила доступа.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\",\"details\":null}}";

    private final JwtDecoder jwtDecoder;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtValidationFilter(GatewayProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwt().jwksUri()).build();
        // Кэширует JWKS и повторно загружает при неизвестном kid (ротация ключей).
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        this.jwtDecoder = decoder;
        this.publicPaths = properties.publicPaths() == null ? List.of() : properties.publicPaths();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }
        try {
            jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
        } catch (JwtException e) {
            return unauthorized(exchange);
        }
        return chain.filter(exchange);
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = UNAUTHORIZED_BODY.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        // До маршрутизации к downstream-сервису
        return -100;
    }
}
