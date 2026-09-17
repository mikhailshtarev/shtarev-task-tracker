package ru.shatrev.tasks.branch;

import java.time.Instant;
import java.util.UUID;

public record BranchResponse(UUID id, String name, UUID parentId, short depth, Instant createdAt, Instant updatedAt) {

    public BranchResponse(UUID id, String name, Instant createdAt, Instant updatedAt) {
        this(id, name, null, (short) 1, createdAt, updatedAt);
    }

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getParentId(), branch.getDepth(),
                branch.getCreatedAt(), branch.getUpdatedAt());
    }
}
