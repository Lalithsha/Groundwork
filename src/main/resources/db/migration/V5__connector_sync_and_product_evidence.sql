-- Operational state for OAuth, selected-scope reconciliation, human feedback,
-- and product-outcome measurement. These tables remain workspace scoped.

CREATE TABLE IF NOT EXISTS connector_oauth_states (
    state_hash TEXT PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    initiated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    provider TEXT NOT NULL CHECK (provider IN ('JIRA', 'CONFLUENCE')),
    scopes TEXT[] NOT NULL DEFAULT '{}',
    selected_resources JSONB NOT NULL DEFAULT '{}'::jsonb,
    code_verifier TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_connector_oauth_state_expiry
    ON connector_oauth_states(expires_at) WHERE consumed_at IS NULL;

CREATE TABLE IF NOT EXISTS connector_sync_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id UUID NOT NULL REFERENCES connector_connections(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    resource_type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED')),
    cursor_before TEXT,
    cursor_after TEXT,
    discovered_count INTEGER NOT NULL DEFAULT 0,
    indexed_count INTEGER NOT NULL DEFAULT 0,
    tombstoned_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_connector_sync_runs_connection
    ON connector_sync_runs(connection_id, started_at DESC);

ALTER TABLE evidence_artifacts ADD COLUMN IF NOT EXISTS last_seen_sync_id UUID;
ALTER TABLE evidence_artifacts ADD CONSTRAINT fk_evidence_last_seen_sync
    FOREIGN KEY (last_seen_sync_id) REFERENCES connector_sync_runs(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS finding_feedback_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    change_set_id UUID NOT NULL REFERENCES change_sets(id) ON DELETE CASCADE,
    finding_id UUID NOT NULL REFERENCES change_findings(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action TEXT NOT NULL CHECK (action IN ('CONFIRMED', 'DISMISSED', 'EDITED', 'USEFUL', 'NOT_USEFUL')),
    reason_code TEXT,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_finding_feedback_change
    ON finding_feedback_events(change_set_id, created_at DESC);

CREATE TABLE IF NOT EXISTS product_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_name TEXT NOT NULL,
    entity_type TEXT,
    entity_id UUID,
    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_product_events_workspace_time
    ON product_events(workspace_id, occurred_at DESC);
