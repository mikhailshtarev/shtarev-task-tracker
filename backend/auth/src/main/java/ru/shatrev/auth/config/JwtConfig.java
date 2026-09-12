package ru.shatrev.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * RSA-ключи JWT из окружения. Поддерживаются два ключа одновременно:
 * текущий (подпись) и предыдущий (только валидация + JWKS, ротация — раздел 6.7).
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtConfig(
        String kid,
        String privateKey,
        String publicKey,
        String previousKid,
        String previousPublicKey,
        String issuer,
        int accessTokenTtlMinutes,
        int refreshTokenTtlDays
) {

    public SignedKey currentKey() {
        return new SignedKey(kid, parsePublicKey(publicKey), parsePrivateKey(privateKey));
    }

    public Optional<VerificationKey> previousKey() {
        if (previousKid == null || previousKid.isBlank()
                || previousPublicKey == null || previousPublicKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new VerificationKey(previousKid, parsePublicKey(previousPublicKey)));
    }

    /** Все ключи для JWKS: текущий + предыдущий (если задан). */
    public List<VerificationKey> allPublicKeys() {
        var all = new java.util.ArrayList<VerificationKey>();
        all.add(new VerificationKey(kid, parsePublicKey(publicKey)));
        previousKey().ifPresent(all::add);
        return List.copyOf(all);
    }

    public record SignedKey(String kid, RSAPublicKey publicKey, PrivateKey privateKey) {
    }

    public record VerificationKey(String kid, RSAPublicKey publicKey) {
    }

    static RSAPublicKey parsePublicKey(String pem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            byte[] bytes = decodePem(pem, "PUBLIC KEY");
            return (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key PEM", e);
        }
    }

    static PrivateKey parsePrivateKey(String pem) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            byte[] bytes = decodePem(pem, "PRIVATE KEY");
            return factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key PEM", e);
        }
    }

    private static byte[] decodePem(String pem, String marker) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException(
                    "JWT RSA key is not configured. Set AUTH_JWT_PUBLIC_KEY / AUTH_JWT_PRIVATE_KEY.");
        }
        String base64 = pem
                .replace("-----BEGIN " + marker + "-----", "")
                .replace("-----END " + marker + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
