package ru.shatrev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.shatrev.auth.entity.LoginAttempt;

import java.time.LocalDateTime;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    long countByEndpointAndIpAddressAndFailedAtAfter(String endpoint, String ipAddress, LocalDateTime since);

    long countByEndpointAndUserIdAndFailedAtAfter(String endpoint, UUID userId, LocalDateTime since);

    long deleteByFailedAtBefore(LocalDateTime before);

    /** Успешный вход: сброс неудачных попыток пользователя. */
    void deleteByUserIdAndEndpoint(UUID userId, String endpoint);
}
