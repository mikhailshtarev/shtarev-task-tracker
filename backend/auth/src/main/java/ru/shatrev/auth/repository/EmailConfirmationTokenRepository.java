package ru.shatrev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.shatrev.auth.entity.EmailConfirmationToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, UUID> {

    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    void deleteByUserId(UUID userId);

    long deleteByExpiresAtBefore(LocalDateTime now);
}
