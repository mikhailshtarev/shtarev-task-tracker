package ru.shatrev.tasks.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
public class BranchPageReader {
    private final JdbcTemplate jdbc;

    public BranchPageReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BranchResponse> read(UUID userId, String query, BranchCursor cursor, int limit) {
        String sql = "SELECT id, name, created_at, updated_at FROM branches "
                + "WHERE user_id = ? AND archived_at IS NULL "
                + "AND (? = '' OR position(lower(?) in lower(name)) > 0) "
                + (cursor == null ? "" : "AND (created_at, id) < (?, ?) ")
                + "ORDER BY created_at DESC, id DESC LIMIT ?";
        Object[] args = cursor == null
                ? new Object[]{userId, query, query, limit}
                : new Object[]{userId, query, query, Timestamp.from(cursor.createdAt()), cursor.id(), limit};
        return jdbc.query(sql, (rs, row) -> new BranchResponse(
                rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), args);
    }
}
