CREATE TABLE work_plans (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES branches(id),
    name VARCHAR(150) NOT NULL,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_work_plans_active_page
    ON work_plans (branch_id, created_at DESC, id DESC)
    WHERE archived_at IS NULL;
