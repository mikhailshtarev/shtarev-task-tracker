package ru.shatrev.auth.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Генерация криптостойких случайных токенов для писем (подтверждение email, сброс пароля). */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
