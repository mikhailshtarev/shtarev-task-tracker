package ru.shatrev.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ru.shatrev.auth.config.JwtConfig;
import ru.shatrev.auth.entity.User;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** B-01, B-02: генерация/валидация access/refresh, kid, чужой ключ, истёкший и изменённый токен. */
class JwtServiceTest {

    private static KeyPair keyPair;
    private static KeyPair foreignKeyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        keyPair = rsaKeyPair();
        foreignKeyPair = rsaKeyPair();
    }

    private JwtConfig config(KeyPair current, String previousKid, KeyPair previous) {
        return new JwtConfig(
                "test-kid",
                pem("PRIVATE KEY", current.getPrivate().getEncoded()),
                pem("PUBLIC KEY", current.getPublic().getEncoded()),
                previousKid,
                previous == null ? "" : pem("PUBLIC KEY", previous.getPublic().getEncoded()),
                "test-issuer",
                15,
                7
        );
    }

    @Test
    void b01_accessTokenCarriesKidAndClaims() {
        JwtService service = new JwtService(config(keyPair, null, null));
        UUID userId = UUID.randomUUID();
        String token = service.generateAccessToken(user(userId));

        Claims claims = service.validateToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo("user@example.com");
        assertThat(claims.get(JwtService.CLAIM_TYPE, String.class)).isEqualTo(JwtService.TYPE_ACCESS);
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getIssuer().toString()).isEqualTo("test-issuer");
        long ttlSeconds = claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(15 * 60);
        // kid в заголовке токена
        String header = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        assertThat(header).contains("test-kid").contains("RS256");
    }

    @Test
    void b01_refreshTokenHasRefreshTypeAndSevenDayTtl() {
        JwtService service = new JwtService(config(keyPair, null, null));
        UUID userId = UUID.randomUUID();
        String token = service.generateRefreshToken(user(userId));

        Claims claims = service.validateToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get(JwtService.CLAIM_TYPE, String.class)).isEqualTo(JwtService.TYPE_REFRESH);
        long ttlSeconds = claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond();
        assertThat(ttlSeconds).isEqualTo(7L * 24 * 60 * 60);
    }

    @Test
    void b02_tokenSignedByForeignKeyIsRejected() {
        JwtService service = new JwtService(config(keyPair, null, null));
        String foreignToken = Jwts.builder()
                .header().keyId("test-kid").and()
                .issuer("test-issuer")
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TYPE, JwtService.TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(foreignKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThat(service.validateToken(foreignToken)).isNull();
    }

    @Test
    void b02_expiredTokenIsRejected() {
        JwtConfig config = config(keyPair, null, null);
        String expired = Jwts.builder()
                .header().keyId(config.kid()).and()
                .issuer(config.issuer())
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TYPE, JwtService.TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now().minus(2, ChronoUnit.HOURS)))
                .expiration(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .signWith(config.currentKey().privateKey(), Jwts.SIG.RS256)
                .compact();
        assertThat(new JwtService(config).validateToken(expired)).isNull();
    }

    @Test
    void b02_tamperedTokenIsRejected() {
        JwtService service = new JwtService(config(keyPair, null, null));
        String token = service.generateAccessToken(user(UUID.randomUUID()));
        String[] parts = token.split("\\.");
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"00000000-0000-0000-0000-000000000000\"}".getBytes());
        String tampered = parts[0] + "." + parts[1] + "." + parts[2];
        assertThat(service.validateToken(tampered)).isNull();
    }

    @Test
    void previousKeyStillValidatesDuringRotation() {
        JwtService service = new JwtService(config(keyPair, "old-kid", foreignKeyPair));
        String signedWithOldKey = Jwts.builder()
                .header().keyId("old-kid").and()
                .issuer("test-issuer")
                .subject(UUID.randomUUID().toString())
                .claim(JwtService.CLAIM_TYPE, JwtService.TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
                .signWith(foreignKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
        assertThat(service.validateToken(signedWithOldKey)).isNotNull();
    }

    private static User user(UUID id) {
        User user = new User();
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        user.setEmail("user@example.com");
        return user;
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String marker, byte[] encoded) {
        return "-----BEGIN " + marker + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded)
                + "\n-----END " + marker + "-----";
    }
}
