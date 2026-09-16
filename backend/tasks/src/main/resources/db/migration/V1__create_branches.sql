CREATE TABLE branches (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_branches_active_page
    ON branches (user_id, created_at DESC, id DESC)
    WHERE archived_at IS NULL;

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    changes JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(64)
);

CREATE INDEX idx_audit_events_entity
    ON audit_events (user_id, entity_type, entity_id, occurred_at DESC, id DESC);
