package ru.shatrev.tasks.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class BranchAudit {
    private final JdbcTemplate jdbc;

    public BranchAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(UUID userId, UUID branchId, String action, String field,
                       String from, String to, Instant now) {
        jdbc.update("INSERT INTO audit_events "
                        + "(id, user_id, entity_type, entity_id, action, changes, occurred_at) "
                        + "VALUES (?, ?, 'branch', ?, ?, "
                        + "jsonb_build_object(CAST(? AS text), "
                        + "jsonb_build_object('from', CAST(? AS text), 'to', CAST(? AS text))), ?)",
                UUID.randomUUID(), userId, branchId, action, field, from, to, Timestamp.from(now));
    }
}
