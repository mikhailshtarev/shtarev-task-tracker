package ru.shatrev.auth.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Argon2id с параметрами раздела 2.5: timeCost=2, memoryCost=65536, parallelism=1. */
@Component
public class PasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_COST = 65536;
    private static final int TIME_COST = 2;

    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_COST, TIME_COST);

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }
}
