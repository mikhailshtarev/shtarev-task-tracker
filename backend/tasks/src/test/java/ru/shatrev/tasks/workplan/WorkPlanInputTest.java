package ru.shatrev.tasks.workplan;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class WorkPlanInputTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void onlyStringNameIsAccepted() {
        assertEquals("План", mapper.readValue("{\"name\":\"План\"}", WorkPlanInput.class).name());
        assertThrows(Exception.class, () -> mapper.readValue("{\"name\":123}", WorkPlanInput.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"name\":\"План\",\"branchId\":\"forged\"}", WorkPlanInput.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"name\":\"План\",\"userId\":\"forged\"}", WorkPlanInput.class));
    }
}
