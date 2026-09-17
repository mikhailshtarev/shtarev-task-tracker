package ru.shatrev.tasks.workplan;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public record WorkPlanInput(String name) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static WorkPlanInput parse(JsonNode input) {
        if (input == null || !input.isObject() || input.size() != 1
                || input.get("name") == null || !input.get("name").isTextual()) {
            throw new IllegalArgumentException("Invalid work plan input");
        }
        return new WorkPlanInput(input.get("name").asText());
    }
}
