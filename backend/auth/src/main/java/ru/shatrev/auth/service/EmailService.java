package ru.shatrev.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Заглушка: письмо логируется вместо реальной отправки (раздел 6.4). */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    public void sendConfirmationEmail(String email, String token) {
        log.info("Confirmation link for {}: /confirm-email?token={}", email, token);
    }

    public void sendResetPasswordEmail(String email, String token) {
        log.info("Reset password link for {}: /reset-password?token={}", email, token);
    }
}
