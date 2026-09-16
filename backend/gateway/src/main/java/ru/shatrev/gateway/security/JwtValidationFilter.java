package ru.shatrev.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.shatrev.gateway.config.GatewayProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Валидация Bearer access-токена для защищённых маршрутов по JWKS auth-сервиса (раздел 6.7).
 * /api/v1/auth/** не проверяется здесь — сервис auth применяет собственные правила доступа.
 */
@Component
public class JwtValidationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNAUTHORIZED_BODY =
            "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\",\"details\":null}}";

    public static final String USER_ID_ATTRIBUTE = JwtValidationFilter.class.getName() + ".userId";
    private final ReactiveJwtDecoder jwtDecoder;
    private final List<String> publicPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtValidationFilter(GatewayProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwt().jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        this.jwtDecoder = decoder;
        this.publicPaths = properties.publicPaths() == null ? List.of() : properties.publicPaths();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest().mutate().headers(headers ->
                headers.headerNames().stream()
                        .filter(name -> name.regionMatches(true, 0, "X-Internal-", 0, 11))
                        .toList().forEach(headers::remove)).build();
        ServerWebExchange clean = exchange.mutate().request(request).build();
        String path = request.getPath().value();
        if (request.getMethod() == HttpMethod.OPTIONS && request.getHeaders().containsHeader("Origin")
                && request.getHeaders().containsHeader("Access-Control-Request-Method")) {
            clean.getResponse().setStatusCode(HttpStatus.OK);
            return clean.getResponse().setComplete();
        }
        if (isPublic(path)) {
            return chain.filter(clean);
        }

        List<String> values = request.getHeaders().getOrEmpty("Authorization");
        if (values.size() != 1 || !values.getFirst().startsWith(BEARER_PREFIX)
                || values.getFirst().length() <= BEARER_PREFIX.length()) {
            return unauthorized(clean);
        }
        return jwtDecoder.decode(values.getFirst().substring(BEARER_PREFIX.length()))
                .publishOn(Schedulers.boundedElastic())
                .map(JwtValidationFilter::userId)
                .filter(java.util.Objects::nonNull)
                .doOnNext(user -> clean.getAttributes().put(USER_ID_ATTRIBUTE, user))
                .hasElement()
                .onErrorReturn(false)
                .flatMap(valid -> valid ? chain.filter(clean) : unauthorized(clean));
    }

    private static UUID userId(Jwt jwt) {
        if (!"access".equals(jwt.getClaimAsString("type")) || jwt.getSubject() == null
                || jwt.getExpiresAt() == null) return null;
        try {
            UUID id = UUID.fromString(jwt.getSubject());
            return id.toString().equals(jwt.getSubject()) ? id : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        var response = exchange.getResponse();
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
