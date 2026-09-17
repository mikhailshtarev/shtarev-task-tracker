ALTER TABLE branches
    ADD COLUMN parent_id UUID,
    ADD COLUMN depth SMALLINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_branches_depth CHECK (depth BETWEEN 1 AND 7),
    ADD CONSTRAINT chk_branches_parent_not_self CHECK (parent_id IS NULL OR parent_id <> id),
    ADD CONSTRAINT chk_branches_root_depth CHECK (parent_id IS NOT NULL OR depth = 1);

ALTER TABLE branches ADD CONSTRAINT uq_branches_user_id_id UNIQUE (user_id, id);
ALTER TABLE branches ADD CONSTRAINT fk_branches_owned_parent
    FOREIGN KEY (user_id, parent_id) REFERENCES branches (user_id, id) ON DELETE RESTRICT;

CREATE INDEX idx_branches_active_siblings
    ON branches (user_id, parent_id, created_at DESC, id DESC)
    WHERE archived_at IS NULL;
