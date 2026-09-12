package ru.shatrev.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import ru.shatrev.auth.entity.RefreshTokenBlacklist;
import ru.shatrev.auth.entity.User;
import ru.shatrev.auth.exception.ApiException;
import ru.shatrev.auth.repository.RefreshTokenBlacklistRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** B-07: повторный INSERT того же jti — нарушение UNIQUE превращается в 401. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenBlacklistServiceTest {

    @Mock
    private RefreshTokenBlacklistRepository repository;

    private RefreshTokenBlacklistService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenBlacklistService(repository);
    }

    @Test
    void duplicateJtiInsertBecomes401() {
        User user = new User();
        when(repository.saveAndFlush(any(RefreshTokenBlacklist.class)))
                .thenThrow(new DataIntegrityViolationException("UNIQUE constraint"));

        assertThatThrownBy(() -> service.addToBlacklist("jti-1", user, LocalDateTime.now().plusDays(7)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                    assertThat(e.getHttpStatus()).isEqualTo(401);
                });
    }

    @Test
    void firstInsertSucceeds() {
        User user = new User();
        when(repository.saveAndFlush(any(RefreshTokenBlacklist.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.addToBlacklist("jti-1", user, LocalDateTime.now().plusDays(7));
    }

    @Test
    void isBlacklistedChecksRepository() {
        when(repository.findByJti("jti-1")).thenReturn(Optional.of(new RefreshTokenBlacklist()));
        when(repository.findByJti("jti-2")).thenReturn(Optional.empty());
        assertThat(service.isBlacklisted("jti-1")).isTrue();
        assertThat(service.isBlacklisted("jti-2")).isFalse();
    }
}
