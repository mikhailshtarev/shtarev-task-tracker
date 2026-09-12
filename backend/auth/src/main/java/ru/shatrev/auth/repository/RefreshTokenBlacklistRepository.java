package ru.shatrev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.shatrev.auth.entity.RefreshTokenBlacklist;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenBlacklistRepository extends JpaRepository<RefreshTokenBlacklist, UUID> {

    Optional<RefreshTokenBlacklist> findByJti(String jti);

    long deleteByExpiresAtBefore(LocalDateTime now);
}
