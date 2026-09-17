package ru.shatrev.tasks.branch;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record BranchInput(@NotNull String name, UUID parentId, boolean parentProvided) {
    public BranchInput(String name) {
        this(name, null, false);
    }

    public BranchInput(String name, UUID parentId) {
        this(name, parentId, parentId != null);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BranchInput parse(JsonNode input) {
        if (input == null || !input.isObject() || input.size() < 1 || input.size() > 2
                || input.get("name") == null || !input.get("name").isTextual()) {
            throw new IllegalArgumentException("Invalid branch input");
        }
        JsonNode parent = input.get("parentId");
        if (input.size() == 2 && parent == null) throw new IllegalArgumentException("Invalid branch input");
        if (parent == null) return new BranchInput(input.get("name").asText());
        if (parent.isNull()) return new BranchInput(input.get("name").asText(), null, true);
        if (!parent.isTextual()) throw new IllegalArgumentException("Invalid parentId");
        UUID id = UUID.fromString(parent.asText());
        if (!id.toString().equals(parent.asText())) throw new IllegalArgumentException("Invalid parentId");
        return new BranchInput(input.get("name").asText(), id, true);
    }
}
