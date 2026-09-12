package ru.shatrev.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Настройки валидации JWT на гейтвее: JWKS auth-сервиса, issuer и публичные пути. */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(Jwt jwt, List<String> publicPaths) {

    public record Jwt(String jwksUri, String issuer) {
    }
}
