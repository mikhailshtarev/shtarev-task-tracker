package ru.shatrev.tasks.branch;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BranchJsonTest {
    @Test
    void unknownFieldsAndScalarCoercionAreRejected() {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("Спорт", mapper.readValue("{\"name\":\"Спорт\"}", BranchInput.class).name());
        UUID parent = UUID.randomUUID();
        assertEquals(parent, mapper.readValue("{\"name\":\"Спорт\",\"parentId\":\"" + parent + "\"}",
                BranchInput.class).parentId());
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"name\":\"Спорт\",\"parentId\":\"wrong\"}", BranchInput.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"name\":\"Спорт\",\"userId\":\"forged\"}", BranchInput.class));
        assertThrows(Exception.class, () -> mapper.readValue("{\"name\":123}", BranchInput.class));
    }
}
