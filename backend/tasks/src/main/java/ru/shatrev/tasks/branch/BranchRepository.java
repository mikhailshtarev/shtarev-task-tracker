package ru.shatrev.tasks.branch;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndUserIdAndArchivedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id and b.userId = :userId")
    Optional<Branch> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);
}
