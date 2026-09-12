package ru.shatrev.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.stereotype.Service;
import ru.shatrev.auth.config.JwtConfig;
import ru.shatrev.auth.entity.User;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Генерация и валидация JWT (RS256, заголовок kid, раздел 6.1).
 * Поддерживает два ключа: текущий — подпись, текущий и предыдущий — валидация.
 */
@Service
public class JwtService {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtConfig config;
    private final Map<String, JwtConfig.VerificationKey> keysByKid = new ConcurrentHashMap<>();

    public JwtService(JwtConfig config) {
        this.config = config;
        for (JwtConfig.VerificationKey key : config.allPublicKeys()) {
            keysByKid.put(key.kid(), key);
        }
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(config.accessTokenTtlMinutes() * 60L, java.time.temporal.ChronoUnit.SECONDS);
        return buildToken(user, TYPE_ACCESS, now, exp);
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(config.refreshTokenTtlDays() * 24L * 60 * 60, java.time.temporal.ChronoUnit.SECONDS);
        return buildToken(user, TYPE_REFRESH, now, exp);
    }

    public Instant getAccessTokenExpiration() {
        return Instant.now().plus(config.accessTokenTtlMinutes() * 60L, java.time.temporal.ChronoUnit.SECONDS);
    }

    /** Валидирует подпись (по kid), срок действия и issuer. Возвращает claims или null. */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .clockSkewSeconds(0)
                    .requireIssuer(config.issuer())
                    .keyLocator(header -> {
                        Object kid = header.get("kid");
                        JwtConfig.VerificationKey key = kid == null ? null : keysByKid.get(kid.toString());
                        if (key == null) {
                            throw new SignatureException("Unknown kid: " + kid);
                        }
                        return key.publicKey();
                    })
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public UUID getUserFromToken(String token) {
        Claims claims = validateToken(token);
        return claims == null ? null : UUID.fromString(claims.getSubject());
    }

    public String getJtiFromToken(String token) {
        Claims claims = validateToken(token);
        return claims == null ? null : claims.getId();
    }

    public Instant getIatFromToken(String token) {
        Claims claims = validateToken(token);
        return claims == null ? null : claims.getIssuedAt().toInstant();
    }

    public String getEmailFromToken(String token) {
        Claims claims = validateToken(token);
        return claims == null ? null : claims.get(CLAIM_EMAIL, String.class);
    }

    public String getTypeFromToken(String token) {
        Claims claims = validateToken(token);
        return claims == null ? null : claims.get(CLAIM_TYPE, String.class);
    }

    public Instant getExpirationFromToken(String token) {
        Claims claims = validateToken(token);
        return claims == null ? null : claims.getExpiration().toInstant();
    }

    public String getKid() {
        return config.kid();
    }

    private String buildToken(User user, String type, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .header().keyId(config.kid()).and()
                .issuer(config.issuer())
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, type)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(config.currentKey().privateKey(), Jwts.SIG.RS256)
                .compact();
    }
}
