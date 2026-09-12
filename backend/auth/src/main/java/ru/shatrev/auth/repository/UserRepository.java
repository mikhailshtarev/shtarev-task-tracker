package ru.shatrev.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.shatrev.auth.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email хранится в нижнем регистре; поиск без учёта регистра. */
    Optional<User> findByEmailIgnoreCase(String email);
}
