package ru.shatrev.tasks.branch;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record BranchInput(@NotNull String name) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static BranchInput parse(JsonNode input) {
        if (input == null || !input.isObject() || input.size() != 1
                || input.get("name") == null || !input.get("name").isTextual()) {
            throw new IllegalArgumentException("Invalid branch input");
        }
        return new BranchInput(input.get("name").asText());
    }
}
