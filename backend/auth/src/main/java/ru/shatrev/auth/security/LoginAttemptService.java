package ru.shatrev.auth.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.auth.entity.LoginAttempt;
import ru.shatrev.auth.entity.User;
import ru.shatrev.auth.repository.LoginAttemptRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rate limiting на таблице login_attempts (раздел 6.2):
 * login — 5 попыток по пользователю / 20 по IP за 15 минут;
 * register и forgot-password — 5 по IP за час; resend-confirmation — 3 по IP за час.
 */
@Service
public class LoginAttemptService {

    public static final String ENDPOINT_LOGIN = "login";
    public static final String ENDPOINT_REGISTER = "register";
    public static final String ENDPOINT_FORGOT_PASSWORD = "forgot-password";
    public static final String ENDPOINT_RESEND_CONFIRMATION = "resend-confirmation";

    private static final int USER_LOGIN_LIMIT = 5;
    private static final int IP_LOGIN_LIMIT = 20;
    private static final int IP_HOURLY_LIMIT = 5;
    private static final int RESEND_HOURLY_LIMIT = 3;

    private final LoginAttemptRepository repository;

    public LoginAttemptService(LoginAttemptRepository repository) {
        this.repository = repository;
    }

    /** user_id может быть NULL — попытка подбора к несуществующему email. */
    public void recordAttempt(User user, String ipAddress, String endpoint) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setIpAddress(ipAddress);
        attempt.setEndpoint(endpoint);
        repository.save(attempt);
    }

    /** Успешный вход: сброс неудачных попыток пользователя (в пределах текущей транзакции). */
    @Transactional
    public void clearAttempts(UUID userId) {
        repository.deleteByUserIdAndEndpoint(userId, ENDPOINT_LOGIN);
    }

    /** ≥ 5 неудачных попыток login пользователя за последние 15 минут. */
    public boolean isBlockedByUser(UUID userId) {
        long count = repository.countByEndpointAndUserIdAndFailedAtAfter(
                ENDPOINT_LOGIN, userId, LocalDateTime.now().minusMinutes(15));
        return count >= USER_LOGIN_LIMIT;
    }

    /** ≥ 20 неудачных попыток login с IP за последние 15 минут. */
    public boolean isBlockedByIp(String ipAddress) {
        long count = repository.countByEndpointAndIpAddressAndFailedAtAfter(
                ENDPOINT_LOGIN, ipAddress, LocalDateTime.now().minusMinutes(15));
        return count >= IP_LOGIN_LIMIT;
    }

    /** register / forgot-password — 5 по IP за час; resend-confirmation — 3 по IP за час. */
    public boolean isRateLimitedByIp(String ipAddress, String endpoint) {
        int limit = ENDPOINT_RESEND_CONFIRMATION.equals(endpoint) ? RESEND_HOURLY_LIMIT : IP_HOURLY_LIMIT;
        long count = repository.countByEndpointAndIpAddressAndFailedAtAfter(
                endpoint, ipAddress, LocalDateTime.now().minusHours(1));
        return count >= limit;
    }

    @Transactional
    @Scheduled(cron = "0 0 */6 * * *")
    public void cleanup() {
        repository.deleteByFailedAtBefore(LocalDateTime.now().minusHours(24));
    }
}
