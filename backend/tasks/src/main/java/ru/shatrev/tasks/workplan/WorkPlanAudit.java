package ru.shatrev.tasks.workplan;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class WorkPlanAudit {
    private final JdbcTemplate jdbc;

    public WorkPlanAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void created(UUID userId, UUID planId, UUID branchId, String name, Instant now) {
        jdbc.update("INSERT INTO audit_events "
                        + "(id, user_id, entity_type, entity_id, action, changes, occurred_at) "
                        + "VALUES (?, ?, 'work_plan', ?, 'created', "
                        + "jsonb_build_object('branchId', jsonb_build_object('from', NULL, 'to', CAST(? AS text)), "
                        + "'name', jsonb_build_object('from', NULL, 'to', CAST(? AS text))), ?)",
                UUID.randomUUID(), userId, planId, branchId.toString(), name, Timestamp.from(now));
    }

    public void renamed(UUID userId, UUID planId, String from, String to, Instant now) {
        jdbc.update("INSERT INTO audit_events "
                        + "(id, user_id, entity_type, entity_id, action, changes, occurred_at) "
                        + "VALUES (?, ?, 'work_plan', ?, 'renamed', "
                        + "jsonb_build_object('name', jsonb_build_object('from', CAST(? AS text), 'to', CAST(? AS text))), ?)",
                UUID.randomUUID(), userId, planId, from, to, Timestamp.from(now));
    }

    public void archived(UUID userId, List<UUID> planIds, Instant now) {
        if (planIds.isEmpty()) return;
        jdbc.batchUpdate("INSERT INTO audit_events "
                        + "(id, user_id, entity_type, entity_id, action, changes, occurred_at) "
                        + "VALUES (?, ?, 'work_plan', ?, 'archived', "
                        + "jsonb_build_object('archivedAt', jsonb_build_object('from', NULL, 'to', CAST(? AS text))), ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        statement.setObject(1, UUID.randomUUID());
                        statement.setObject(2, userId);
                        statement.setObject(3, planIds.get(index));
                        statement.setString(4, now.toString());
                        statement.setTimestamp(5, Timestamp.from(now));
                    }

                    @Override
                    public int getBatchSize() {
                        return planIds.size();
                    }
                });
    }
}
