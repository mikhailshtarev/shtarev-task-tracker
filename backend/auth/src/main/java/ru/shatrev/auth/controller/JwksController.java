package ru.shatrev.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.shatrev.auth.config.JwtConfig;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Публичный JWKS для валидации access-токенов Gateway и другими сервисами (раздел 4.13). */
@RestController
@RequestMapping("/api/v1/auth")
public class JwksController {

    private final JwtConfig jwtConfig;

    public JwksController(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, List<Map<String, Object>>> jwks() {
        List<Map<String, Object>> keys = jwtConfig.allPublicKeys().stream()
                .map(JwksController::toJwk)
                .toList();
        return Map.of("keys", keys);
    }

    private static Map<String, Object> toJwk(JwtConfig.VerificationKey key) {
        RSAPublicKey rsa = key.publicKey();
        Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", key.kid(),
                "n", urlEncoder.encodeToString(stripSignByte(rsa.getModulus())),
                "e", urlEncoder.encodeToString(stripSignByte(rsa.getPublicExponent()))
        );
    }

    /** Убирает ведущий нулевой байт положительного BigInteger. */
    private static byte[] stripSignByte(java.math.BigInteger value) {
        byte[] array = value.toByteArray();
        if (array.length > 1 && array[0] == 0) {
            byte[] trimmed = new byte[array.length - 1];
            System.arraycopy(array, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return array;
    }
}
