package ru.shatrev.tasks.workplan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class WorkPlanReader {
    private final JdbcTemplate jdbc;

    public WorkPlanReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkPlanResponse> page(UUID userId, UUID branchId, String query, WorkPlanCursor cursor, int limit) {
        String sql = "SELECT p.id, p.branch_id, p.name, p.created_at, p.updated_at "
                + "FROM work_plans p JOIN branches b ON b.id = p.branch_id "
                + "WHERE b.user_id = ? AND b.archived_at IS NULL AND p.branch_id = ? AND p.archived_at IS NULL "
                + "AND (? = '' OR position(lower(?) in lower(p.name)) > 0) "
                + (cursor == null ? "" : "AND (p.created_at, p.id) < (?, ?) ")
                + "ORDER BY p.created_at DESC, p.id DESC LIMIT ?";
        Object[] args = cursor == null
                ? new Object[]{userId, branchId, query, query, limit}
                : new Object[]{userId, branchId, query, query, Timestamp.from(cursor.createdAt()), cursor.id(), limit};
        return jdbc.query(sql, (rs, row) -> response(rs), args);
    }

    public Optional<WorkPlanResponse> active(UUID userId, UUID id) {
        List<WorkPlanResponse> rows = jdbc.query("SELECT p.id, p.branch_id, p.name, p.created_at, p.updated_at "
                        + "FROM work_plans p JOIN branches b ON b.id = p.branch_id "
                        + "WHERE p.id = ? AND b.user_id = ? AND b.archived_at IS NULL AND p.archived_at IS NULL",
                (rs, row) -> response(rs), id, userId);
        return rows.stream().findFirst();
    }

    public Optional<UUID> branchIdOf(UUID id) {
        List<UUID> rows = jdbc.query("SELECT branch_id FROM work_plans WHERE id = ?",
                (rs, row) -> rs.getObject("branch_id", UUID.class), id);
        return rows.stream().findFirst();
    }

    private static WorkPlanResponse response(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new WorkPlanResponse(rs.getObject("id", UUID.class), rs.getObject("branch_id", UUID.class),
                rs.getString("name"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
