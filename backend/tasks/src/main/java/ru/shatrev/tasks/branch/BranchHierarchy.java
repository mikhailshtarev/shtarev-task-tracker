package ru.shatrev.tasks.branch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class BranchHierarchy {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final BranchRepository branches;

    public BranchHierarchy(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, BranchRepository branches) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.branches = branches;
    }

    /** Lock the entire ancestry, root first, so an ancestor archive cannot race a child creation. */
    public short lockActiveParent(UUID userId, UUID parentId) {
        List<UUID> ancestry = jdbc.query("WITH RECURSIVE ancestors AS ("
                        + "SELECT id, parent_id FROM branches WHERE id = ? AND user_id = ? "
                        + "UNION ALL SELECT b.id, b.parent_id FROM branches b "
                        + "JOIN ancestors a ON a.parent_id = b.id WHERE b.user_id = ?) "
                        + "SELECT id FROM ancestors",
                (rs, row) -> rs.getObject("id", UUID.class), parentId, userId, userId);
        if (ancestry.isEmpty()) throw ApiFailure.notFound();
        Collections.reverse(ancestry);
        Branch parent = null;
        for (UUID id : ancestry) {
            Branch locked = branches.lockOwned(id, userId).orElseThrow(ApiFailure::notFound);
            if (locked.getArchivedAt() != null) throw ApiFailure.notFound();
            parent = locked;
        }
        if (parent == null || !parent.getId().equals(parentId)) throw ApiFailure.notFound();
        if (parent.getDepth() >= 7) {
            throw ApiFailure.validation("parentId", "Максимальная глубина проектов — 7 уровней");
        }
        return (short) (parent.getDepth() + 1);
    }

    /** Locks all descendants in one query and returns their IDs, including already archived ones. */
    public List<UUID> lockSubtree(UUID userId, UUID rootId) {
        return jdbc.query("WITH RECURSIVE descendants AS ("
                        + "SELECT id, depth FROM branches WHERE id = ? AND user_id = ? "
                        + "UNION ALL SELECT b.id, b.depth FROM branches b "
                        + "JOIN descendants d ON b.parent_id = d.id WHERE b.user_id = ?) "
                        + "SELECT b.id FROM branches b JOIN descendants d ON d.id = b.id "
                        + "ORDER BY d.depth, b.id FOR UPDATE OF b",
                (rs, row) -> rs.getObject("id", UUID.class), rootId, userId, userId);
    }

    public List<UUID> archiveActiveDescendants(List<UUID> subtreeIds, UUID rootId, Instant now) {
        List<UUID> descendants = new ArrayList<>(subtreeIds);
        descendants.remove(rootId);
        if (descendants.isEmpty()) return List.of();
        return namedJdbc.query("UPDATE branches SET archived_at = :now, updated_at = :now "
                        + "WHERE id IN (:ids) AND archived_at IS NULL RETURNING id",
                new MapSqlParameterSource().addValue("now", Timestamp.from(now)).addValue("ids", descendants),
                (rs, row) -> rs.getObject("id", UUID.class));
    }
}
