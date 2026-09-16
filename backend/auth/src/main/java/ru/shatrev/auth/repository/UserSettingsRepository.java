package ru.shatrev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.shatrev.auth.entity.UserSettings;

import java.util.Optional;
import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {

    Optional<UserSettings> findByUserId(UUID userId);
}
