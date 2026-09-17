package ru.shatrev.tasks.workplan;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkPlanRepository extends JpaRepository<WorkPlan, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WorkPlan p where p.id = :id and p.branchId = :branchId")
    Optional<WorkPlan> lockInBranch(@Param("id") UUID id, @Param("branchId") UUID branchId);
}
