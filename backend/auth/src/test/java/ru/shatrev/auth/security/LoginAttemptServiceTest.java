package ru.shatrev.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.shatrev.auth.entity.LoginAttempt;
import ru.shatrev.auth.repository.LoginAttemptRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** B-06: границы лимитов 5/15 мин (user), 20/15 мин (IP), 5/час, 3/час. */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private LoginAttemptRepository repository;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(repository);
    }

    @Test
    void userBlockedExactlyAtFifthFailure() {
        UUID userId = UUID.randomUUID();
        when(repository.countByEndpointAndUserIdAndFailedAtAfter(eq("login"), eq(userId), any()))
                .thenReturn(4L);
        assertThat(service.isBlockedByUser(userId)).isFalse();

        when(repository.countByEndpointAndUserIdAndFailedAtAfter(eq("login"), eq(userId), any()))
                .thenReturn(5L);
        assertThat(service.isBlockedByUser(userId)).isTrue();
    }

    @Test
    void userWindowIsFifteenMinutes() {
        UUID userId = UUID.randomUUID();
        when(repository.countByEndpointAndUserIdAndFailedAtAfter(eq("login"), eq(userId), any()))
                .thenReturn(5L);
        service.isBlockedByUser(userId);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).countByEndpointAndUserIdAndFailedAtAfter(eq("login"), eq(userId), captor.capture());
        assertThat(captor.getValue()).isBetween(
                LocalDateTime.now().minusMinutes(15).minusSeconds(5),
                LocalDateTime.now().minusMinutes(15).plusSeconds(5));
    }

    @Test
    void ipBlockedExactlyAtTwentiethFailure() {
        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("login"), eq("1.2.3.4"), any()))
                .thenReturn(19L);
        assertThat(service.isBlockedByIp("1.2.3.4")).isFalse();

        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("login"), eq("1.2.3.4"), any()))
                .thenReturn(20L);
        assertThat(service.isBlockedByIp("1.2.3.4")).isTrue();
    }

    @Test
    void hourlyEndpointsLimitedAtFive() {
        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("register"), eq("1.2.3.4"), any()))
                .thenReturn(4L);
        assertThat(service.isRateLimitedByIp("1.2.3.4", "register")).isFalse();

        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("register"), eq("1.2.3.4"), any()))
                .thenReturn(5L);
        assertThat(service.isRateLimitedByIp("1.2.3.4", "register")).isTrue();

        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("forgot-password"), eq("1.2.3.4"), any()))
                .thenReturn(5L);
        assertThat(service.isRateLimitedByIp("1.2.3.4", "forgot-password")).isTrue();
    }

    @Test
    void resendConfirmationLimitedAtThree() {
        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("resend-confirmation"), eq("1.2.3.4"), any()))
                .thenReturn(3L);
        assertThat(service.isRateLimitedByIp("1.2.3.4", "resend-confirmation")).isTrue();

        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(eq("resend-confirmation"), eq("1.2.3.4"), any()))
                .thenReturn(2L);
        assertThat(service.isRateLimitedByIp("1.2.3.4", "resend-confirmation")).isFalse();
    }

    @Test
    void hourlyWindowIsOneHour() {
        when(repository.countByEndpointAndIpAddressAndFailedAtAfter(anyString(), anyString(), any()))
                .thenReturn(0L);
        service.isRateLimitedByIp("1.2.3.4", "forgot-password");

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).countByEndpointAndIpAddressAndFailedAtAfter(eq("forgot-password"), eq("1.2.3.4"), captor.capture());
        assertThat(captor.getValue()).isBetween(
                LocalDateTime.now().minusHours(1).minusSeconds(5),
                LocalDateTime.now().minusHours(1).plusSeconds(5));
    }

    @Test
    void recordAttemptKeepsNullUserForUnknownEmail() {
        service.recordAttempt(null, "1.2.3.4", "login");
        ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getIpAddress()).isEqualTo("1.2.3.4");
        assertThat(captor.getValue().getEndpoint()).isEqualTo("login");
    }
}
