package ru.shatrev.tasks.navigation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class TreeReader {
    private final JdbcTemplate jdbc;

    public TreeReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Row> root(UUID userId, TreeCursor cursor, int limit) {
        String sql = "WITH nodes AS (SELECT 0 AS rank, id, 'branch' AS type, parent_id, name, depth, created_at, "
                + "(EXISTS (SELECT 1 FROM branches c WHERE c.parent_id = b.id AND c.user_id = b.user_id "
                + "AND c.archived_at IS NULL) OR EXISTS (SELECT 1 FROM work_plans p WHERE p.branch_id = b.id "
                + "AND p.archived_at IS NULL)) AS has_children FROM branches b "
                + "WHERE b.user_id = ? AND b.parent_id IS NULL AND b.archived_at IS NULL) ";
        return query(sql, new Object[]{userId}, cursor, limit);
    }

    public List<Row> children(UUID userId, UUID branchId, TreeCursor cursor, int limit) {
        String sql = "WITH nodes AS ("
                + "SELECT 0 AS rank, b.id, 'branch' AS type, b.parent_id, b.name, b.depth, b.created_at, "
                + "(EXISTS (SELECT 1 FROM branches c WHERE c.parent_id = b.id AND c.user_id = b.user_id "
                + "AND c.archived_at IS NULL) OR EXISTS (SELECT 1 FROM work_plans p WHERE p.branch_id = b.id "
                + "AND p.archived_at IS NULL)) AS has_children FROM branches b "
                + "WHERE b.user_id = ? AND b.parent_id = ? AND b.archived_at IS NULL "
                + "UNION ALL "
                + "SELECT 1 AS rank, p.id, 'work_plan' AS type, p.branch_id AS parent_id, p.name, "
                + "CAST(b.depth + 1 AS smallint) AS depth, p.created_at, false AS has_children "
                + "FROM work_plans p JOIN branches b ON b.id = p.branch_id "
                + "WHERE b.user_id = ? AND b.id = ? AND b.archived_at IS NULL AND p.archived_at IS NULL) ";
        return query(sql, new Object[]{userId, branchId, userId, branchId}, cursor, limit);
    }

    public boolean activePlan(UUID userId, UUID planId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM work_plans p JOIN branches b ON b.id = p.branch_id "
                        + "WHERE p.id = ? AND p.archived_at IS NULL AND b.user_id = ? AND b.archived_at IS NULL",
                Integer.class, planId, userId);
        return count != null && count > 0;
    }

    private List<Row> query(String cte, Object[] context, TreeCursor cursor, int limit) {
        String sql = cte + "SELECT rank, id, type, parent_id, name, depth, created_at, has_children FROM nodes "
                + (cursor == null ? "" : "WHERE (rank > ? OR (rank = ? AND (created_at, id) < (?, ?))) ")
                + "ORDER BY rank, created_at DESC, id DESC LIMIT ?";
        Object[] args = new Object[context.length + (cursor == null ? 1 : 5)];
        System.arraycopy(context, 0, args, 0, context.length);
        if (cursor != null) {
            args[context.length] = cursor.rank();
            args[context.length + 1] = cursor.rank();
            args[context.length + 2] = Timestamp.from(cursor.createdAt());
            args[context.length + 3] = cursor.id();
        }
        args[args.length - 1] = limit;
        return jdbc.query(sql, (rs, row) -> new Row(
                new TreeNode(rs.getObject("id", UUID.class), rs.getString("type"),
                        rs.getObject("parent_id", UUID.class), rs.getString("name"),
                        rs.getShort("depth"), rs.getBoolean("has_children")),
                rs.getInt("rank"), rs.getTimestamp("created_at").toInstant()), args);
    }

    public record Row(TreeNode node, int rank, Instant createdAt) {
    }
}
