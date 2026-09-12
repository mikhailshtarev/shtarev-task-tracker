package ru.shatrev.auth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shatrev.auth.repository.EmailConfirmationTokenRepository;
import ru.shatrev.auth.repository.PasswordResetTokenRepository;

import java.time.LocalDateTime;

/** Фоновая очистка истёкших email- и reset-токенов (раздел 6, cron каждые 6 часов). */
@Service
public class TokenCleanupService {

    private final EmailConfirmationTokenRepository emailConfirmationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public TokenCleanupService(EmailConfirmationTokenRepository emailConfirmationTokenRepository,
                               PasswordResetTokenRepository passwordResetTokenRepository) {
        this.emailConfirmationTokenRepository = emailConfirmationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    @Scheduled(cron = "0 15 */6 * * *")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        emailConfirmationTokenRepository.deleteByExpiresAtBefore(now);
        passwordResetTokenRepository.deleteByExpiresAtBefore(now);
    }
}
