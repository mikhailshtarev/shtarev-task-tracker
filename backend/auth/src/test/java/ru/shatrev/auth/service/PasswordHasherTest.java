package ru.shatrev.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** B-04: Argon2id — хеш и проверка, параметры 2/65536/1, хеш не обратим. */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashAndVerifyRoundTrip() {
        String hash = hasher.hash("SecurePass1");
        assertThat(hash).isNotEqualTo("SecurePass1");
        assertThat(hasher.matches("SecurePass1", hash)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = hasher.hash("SecurePass1");
        assertThat(hasher.matches("OtherPass1", hash)).isFalse();
    }

    @Test
    void hashUsesArgon2idWithPlannedParameters() {
        String hash = hasher.hash("SecurePass1");
        // PHC-строка вида $argon2id$v=19$m=65536,t=2,p=1$...
        assertThat(hash).startsWith("$argon2id$");
        assertThat(hash).contains("m=65536,t=2,p=1");
    }

    @Test
    void samePasswordYieldsDifferentSalts() {
        assertThat(hasher.hash("SecurePass1")).isNotEqualTo(hasher.hash("SecurePass1"));
    }

    @Test
    void nullInputsDoNotMatch() {
        assertThat(hasher.matches(null, hasher.hash("x1Aaaaa"))).isFalse();
        assertThat(hasher.matches("x1Aaaaa", null)).isFalse();
    }
}
