package ru.shatrev.auth.service;

import org.springframework.stereotype.Service;

/** Cookie refreshToken: HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=604800 (раздел 4.5). */
@Service
public class CookieService {

    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final long maxAgeSeconds;

    public CookieService(@org.springframework.beans.factory.annotation.Value(
            "${auth.cookie.refresh-token-max-age-seconds:604800}") long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public org.springframework.http.ResponseCookie buildRefreshTokenCookie(String token) {
        return org.springframework.http.ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** Очистка cookie при logout: Max-Age=0. */
    public org.springframework.http.ResponseCookie buildClearRefreshTokenCookie() {
        return org.springframework.http.ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
