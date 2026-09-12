package ru.shatrev.auth.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.shatrev.auth.entity.PasswordHistory;

import java.util.List;
import java.util.UUID;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, java.util.UUID> {

    /** Последние N хешей паролей пользователя (N=5 по требованиям). */
    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long deleteByUserIdAndIdNotIn(UUID userId, List<java.util.UUID> keepIds);
}
