package ru.shatrev.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** B-05: SHA-256 хеширование токенов подтверждения/сброса. */
class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void producesDeterministicHexSha256() {
        String hash = hasher.hash("some-token");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hasher.hash("some-token")).isEqualTo(hash);
    }

    @Test
    void differentTokensProduceDifferentHashes() {
        assertThat(hasher.hash("token-1")).isNotEqualTo(hasher.hash("token-2"));
    }

    @Test
    void rawTokenIsNotRecoverableFromHash() {
        String raw = "very-secret-raw-token-value";
        assertThat(hasher.hash(raw)).doesNotContain(raw);
    }
}
