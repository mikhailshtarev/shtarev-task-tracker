package ru.shatrev.tasks.workplan;

import java.time.Instant;
import java.util.UUID;

public record WorkPlanResponse(UUID id, UUID branchId, String name, Instant createdAt, Instant updatedAt) {
    public static WorkPlanResponse from(WorkPlan plan) {
        return new WorkPlanResponse(plan.getId(), plan.getBranchId(), plan.getName(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
