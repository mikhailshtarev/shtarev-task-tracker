package ru.shatrev.tasks.workplan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class WorkPlanCascade {
    private final JdbcTemplate jdbc;
    private final WorkPlanAudit audit;

    public WorkPlanCascade(JdbcTemplate jdbc, WorkPlanAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Called only after the owning branch has been locked inside its archive transaction. */
    public void archiveActive(UUID userId, UUID branchId, Instant now) {
        List<UUID> changed = jdbc.query("UPDATE work_plans SET archived_at = ?, updated_at = ? "
                        + "WHERE branch_id = ? AND archived_at IS NULL RETURNING id",
                (rs, row) -> rs.getObject("id", UUID.class),
                Timestamp.from(now), Timestamp.from(now), branchId);
        audit.archived(userId, changed, now);
    }
}
