-- Groundwork engineering-evidence platform. All objects are additive so the
-- existing document-intelligence workflows remain operational.

CREATE TABLE IF NOT EXISTS connector_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    provider TEXT NOT NULL CHECK (provider IN ('GITHUB', 'JIRA', 'CONFLUENCE', 'MANUAL', 'DEMO')),
    external_account_id TEXT NOT NULL,
    display_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('PENDING', 'ACTIVE', 'DEGRADED', 'REVOKED', 'FAILED')),
    scopes TEXT[] NOT NULL DEFAULT '{}',
    encrypted_credentials TEXT,
    credential_key_version INTEGER,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_synced_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, provider, external_account_id)
);
CREATE INDEX IF NOT EXISTS idx_connections_workspace_status
    ON connector_connections(workspace_id, status);

CREATE TABLE IF NOT EXISTS connector_sync_cursors (
    connection_id UUID NOT NULL REFERENCES connector_connections(id) ON DELETE CASCADE,
    resource_type TEXT NOT NULL,
    cursor_value TEXT,
    last_success_at TIMESTAMPTZ,
    last_error TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (connection_id, resource_type)
);

CREATE TABLE IF NOT EXISTS webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id UUID REFERENCES connector_connections(id) ON DELETE SET NULL,
    provider TEXT NOT NULL,
    provider_delivery_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    event_action TEXT,
    signature_valid BOOLEAN NOT NULL,
    payload_hash TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACCEPTED'
        CHECK (status IN ('ACCEPTED', 'PROCESSING', 'PROCESSED', 'IGNORED', 'FAILED')),
    error_message TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    UNIQUE (provider, provider_delivery_id)
);
CREATE INDEX IF NOT EXISTS idx_webhook_deliveries_status_received
    ON webhook_deliveries(status, received_at);

CREATE TABLE IF NOT EXISTS integration_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'RETRYING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by TEXT,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_integration_outbox_claim
    ON integration_outbox(status, available_at, created_at);

CREATE TABLE IF NOT EXISTS evidence_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    connection_id UUID REFERENCES connector_connections(id) ON DELETE SET NULL,
    source_provider TEXT NOT NULL,
    external_id TEXT NOT NULL,
    artifact_type TEXT NOT NULL CHECK (artifact_type IN (
        'DOCUMENT', 'REPOSITORY', 'REQUIREMENT', 'ISSUE', 'PULL_REQUEST', 'COMMIT',
        'FILE', 'API_CONTRACT', 'ADR', 'RUNBOOK', 'TEST_RUN', 'BUILD',
        'SECURITY_SCAN', 'DEPLOYMENT', 'INCIDENT', 'APPROVAL', 'RELEASE'
    )),
    title TEXT NOT NULL,
    canonical_url TEXT,
    lifecycle_state TEXT NOT NULL DEFAULT 'CURRENT'
        CHECK (lifecycle_state IN ('CURRENT', 'SUPERSEDED', 'DELETED', 'INACCESSIBLE')),
    source_acl JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, source_provider, external_id)
);
CREATE INDEX IF NOT EXISTS idx_evidence_artifacts_workspace_type
    ON evidence_artifacts(workspace_id, artifact_type, lifecycle_state);

CREATE TABLE IF NOT EXISTS evidence_artifact_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id UUID NOT NULL REFERENCES evidence_artifacts(id) ON DELETE CASCADE,
    source_version TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    embedding VECTOR(1536),
    embedding_model TEXT,
    embedding_version TEXT,
    content_tsv TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (artifact_id, source_version)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_evidence_current_version
    ON evidence_artifact_versions(artifact_id) WHERE valid_to IS NULL;
CREATE INDEX IF NOT EXISTS idx_evidence_version_tsv
    ON evidence_artifact_versions USING GIN(content_tsv);
CREATE INDEX IF NOT EXISTS idx_evidence_version_vector
    ON evidence_artifact_versions USING ivfflat (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS evidence_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    source_artifact_id UUID NOT NULL REFERENCES evidence_artifacts(id) ON DELETE CASCADE,
    target_artifact_id UUID NOT NULL REFERENCES evidence_artifacts(id) ON DELETE CASCADE,
    relationship_type TEXT NOT NULL CHECK (relationship_type IN (
        'IMPLEMENTS', 'CHANGES', 'VALIDATES', 'DOCUMENTS', 'DEPENDS_ON',
        'OWNED_BY', 'GOVERNED_BY', 'SUPERSEDES', 'DEPLOYED_AS', 'CAUSED',
        'MITIGATED_BY', 'APPROVED_BY', 'REFERENCES'
    )),
    provenance_type TEXT NOT NULL CHECK (provenance_type IN ('EXPLICIT', 'RULE', 'AI_INFERRED')),
    provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence DOUBLE PRECISION,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (source_artifact_id <> target_artifact_id),
    UNIQUE (source_artifact_id, target_artifact_id, relationship_type, provenance_type)
);
CREATE INDEX IF NOT EXISTS idx_evidence_rel_source
    ON evidence_relationships(workspace_id, source_artifact_id) WHERE valid_to IS NULL;
CREATE INDEX IF NOT EXISTS idx_evidence_rel_target
    ON evidence_relationships(workspace_id, target_artifact_id) WHERE valid_to IS NULL;

CREATE TABLE IF NOT EXISTS change_sets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    connection_id UUID REFERENCES connector_connections(id) ON DELETE SET NULL,
    repository_external_id TEXT NOT NULL,
    repository_full_name TEXT NOT NULL,
    pull_request_number INTEGER,
    external_change_id TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    author_login TEXT,
    base_sha TEXT NOT NULL,
    head_sha TEXT NOT NULL,
    source_branch TEXT,
    target_branch TEXT,
    state TEXT NOT NULL DEFAULT 'OPEN'
        CHECK (state IN ('DRAFT', 'OPEN', 'MERGED', 'CLOSED')),
    canonical_url TEXT,
    current_analysis_status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (current_analysis_status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED', 'STALE')),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    opened_at TIMESTAMPTZ,
    merged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, repository_external_id, external_change_id, head_sha)
);
CREATE INDEX IF NOT EXISTS idx_change_sets_workspace_updated
    ON change_sets(workspace_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_change_sets_repo_pr
    ON change_sets(workspace_id, repository_external_id, pull_request_number);

CREATE TABLE IF NOT EXISTS analysis_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_set_id UUID NOT NULL REFERENCES change_sets(id) ON DELETE CASCADE,
    head_sha TEXT NOT NULL,
    analyzer_version TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'RETRYING', 'COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED', 'SUPERSEDED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by TEXT,
    last_error TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (change_set_id, head_sha, analyzer_version)
);
CREATE INDEX IF NOT EXISTS idx_analysis_jobs_claim
    ON analysis_jobs(status, available_at, created_at);

CREATE TABLE IF NOT EXISTS change_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_set_id UUID NOT NULL REFERENCES change_sets(id) ON DELETE CASCADE,
    analysis_job_id UUID REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    finding_key TEXT NOT NULL,
    analyzer TEXT NOT NULL,
    analyzer_version TEXT NOT NULL,
    category TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    statement TEXT NOT NULL,
    deterministic BOOLEAN NOT NULL,
    evidence_status TEXT NOT NULL CHECK (evidence_status IN ('SUPPORTED', 'PARTIALLY_SUPPORTED', 'UNSUPPORTED')),
    confidence TEXT CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_status TEXT NOT NULL DEFAULT 'UNREVIEWED'
        CHECK (review_status IN ('UNREVIEWED', 'CONFIRMED', 'DISMISSED', 'EDITED')),
    review_reason TEXT,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (change_set_id, analyzer_version, finding_key)
);
CREATE INDEX IF NOT EXISTS idx_findings_change_severity
    ON change_findings(change_set_id, severity, created_at);

CREATE TABLE IF NOT EXISTS evidence_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT,
    active_version INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, name)
);

CREATE TABLE IF NOT EXISTS evidence_policy_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id UUID NOT NULL REFERENCES evidence_policies(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    rule_type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    definition JSONB NOT NULL,
    definition_hash TEXT NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (policy_id, version)
);

CREATE TABLE IF NOT EXISTS evidence_policy_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_set_id UUID NOT NULL REFERENCES change_sets(id) ON DELETE CASCADE,
    policy_version_id UUID NOT NULL REFERENCES evidence_policy_versions(id) ON DELETE RESTRICT,
    result TEXT NOT NULL CHECK (result IN ('PASS', 'FAIL', 'UNKNOWN', 'EXEMPTED')),
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    message TEXT NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (change_set_id, policy_version_id)
);

CREATE TABLE IF NOT EXISTS evidence_policy_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_set_id UUID NOT NULL REFERENCES change_sets(id) ON DELETE CASCADE,
    policy_version_id UUID NOT NULL REFERENCES evidence_policy_versions(id) ON DELETE RESTRICT,
    rationale TEXT NOT NULL,
    approved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS change_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    change_set_id UUID REFERENCES change_sets(id) ON DELETE CASCADE,
    release_record_id UUID,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_label TEXT NOT NULL,
    decision TEXT NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED')),
    rationale TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS release_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    repository_full_name TEXT NOT NULL,
    base_ref TEXT,
    head_ref TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'READY', 'APPROVED', 'REJECTED', 'RELEASED')),
    manifest JSONB NOT NULL,
    manifest_hash TEXT NOT NULL,
    frozen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, manifest_hash)
);

ALTER TABLE change_approvals
    ADD CONSTRAINT fk_change_approval_release
    FOREIGN KEY (release_record_id) REFERENCES release_records(id) ON DELETE CASCADE;

CREATE TABLE IF NOT EXISTS release_evidence_items (
    release_record_id UUID NOT NULL REFERENCES release_records(id) ON DELETE CASCADE,
    evidence_artifact_version_id UUID REFERENCES evidence_artifact_versions(id) ON DELETE RESTRICT,
    change_set_id UUID REFERENCES change_sets(id) ON DELETE RESTRICT,
    evidence_type TEXT NOT NULL,
    evidence_digest TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (release_record_id, evidence_type, evidence_digest),
    CHECK (evidence_artifact_version_id IS NOT NULL OR change_set_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_policy_workspace
    ON evidence_policies(workspace_id, enabled);
CREATE INDEX IF NOT EXISTS idx_policy_eval_change
    ON evidence_policy_evaluations(change_set_id, result);
CREATE INDEX IF NOT EXISTS idx_release_workspace_created
    ON release_records(workspace_id, created_at DESC);
