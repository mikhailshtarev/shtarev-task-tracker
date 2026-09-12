package ru.shatrev.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** CORS-whitelist origins из окружения; также используется для проверки Origin на /refresh и /logout. */
@ConfigurationProperties(prefix = "auth.cors")
public record CorsConfig(List<String> allowedOrigins) {

    public boolean isOriginAllowed(String origin) {
        return origin != null && allowedOrigins().contains(origin);
    }
}
