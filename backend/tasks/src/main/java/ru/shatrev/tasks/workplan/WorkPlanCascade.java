package ru.shatrev.tasks.workplan;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class WorkPlanCascade {
    private final NamedParameterJdbcTemplate jdbc;
    private final WorkPlanAudit audit;

    public WorkPlanCascade(NamedParameterJdbcTemplate jdbc, WorkPlanAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Called only after the owning branch has been locked inside its archive transaction. */
    public void archiveActive(UUID userId, UUID branchId, Instant now) {
        archiveActive(userId, List.of(branchId), now);
    }

    public void archiveActive(UUID userId, List<UUID> branchIds, Instant now) {
        if (branchIds.isEmpty()) return;
        List<UUID> changed = jdbc.query("UPDATE work_plans SET archived_at = :now, updated_at = :now "
                        + "WHERE branch_id IN (:ids) AND archived_at IS NULL RETURNING id",
                new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("ids", branchIds),
                (rs, row) -> rs.getObject("id", UUID.class));
        audit.archived(userId, changed, now);
    }
}
