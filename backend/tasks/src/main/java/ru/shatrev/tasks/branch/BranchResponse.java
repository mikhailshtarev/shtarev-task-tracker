package ru.shatrev.tasks.branch;

import java.time.Instant;
import java.util.UUID;

public record BranchResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getCreatedAt(), branch.getUpdatedAt());
    }
}
