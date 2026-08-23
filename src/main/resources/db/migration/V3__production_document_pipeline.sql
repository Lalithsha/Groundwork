-- First-class source documents, durable ingestion, tenant membership, and
-- chunk metadata. Existing rows remain valid and searchable.

CREATE TABLE IF NOT EXISTS source_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    media_type TEXT NOT NULL,
    source_type TEXT NOT NULL,
    raw_content TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'PROCESSING', 'READY', 'FAILED', 'DELETED')),
    version INTEGER NOT NULL DEFAULT 1,
    embedding_model TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (workspace_id, title, version),
    UNIQUE (workspace_id, content_hash)
);

ALTER TABLE documents ADD COLUMN IF NOT EXISTS document_id UUID;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS chunk_index INTEGER;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS token_count INTEGER;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS section_title TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS page_number INTEGER;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS embedding_model TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS embedding_version TEXT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_document_chunks_source'
    ) THEN
        ALTER TABLE documents
            ADD CONSTRAINT fk_document_chunks_source
            FOREIGN KEY (document_id) REFERENCES source_documents(id) ON DELETE CASCADE;
    END IF;
END $$;

ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_content_hash_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_document_chunk_hash
    ON documents (document_id, content_hash, chunk_index)
    WHERE document_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_legacy_chunk_hash
    ON documents (content_hash)
    WHERE document_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_document_chunks_source ON documents(document_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_source_documents_workspace ON source_documents(workspace_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_source_content_workspace_including_global
    ON source_documents (COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid), content_hash)
    WHERE status <> 'DELETED';

CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES source_documents(id) ON DELETE CASCADE,
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'RETRYING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    progress_current INTEGER NOT NULL DEFAULT 0,
    progress_total INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_ingestion_document
    ON ingestion_jobs(document_id)
    WHERE status IN ('QUEUED', 'RUNNING', 'RETRYING');
CREATE INDEX IF NOT EXISTS idx_ingestion_jobs_claim
    ON ingestion_jobs(status, available_at, created_at);

ALTER TABLE workspaces ADD COLUMN IF NOT EXISTS owner_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS workspace_memberships (
    workspace_id UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'EDITOR', 'VIEWER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_workspace_memberships_user ON workspace_memberships(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active ON refresh_tokens(user_id, revoked, expires_at);

ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE;
ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS progress_current INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS progress_total INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS available_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS locked_at TIMESTAMPTZ;
ALTER TABLE reindex_jobs ADD COLUMN IF NOT EXISTS locked_by TEXT;
ALTER TABLE reindex_jobs DROP CONSTRAINT IF EXISTS reindex_jobs_status_check;
ALTER TABLE reindex_jobs ADD CONSTRAINT reindex_jobs_status_check
    CHECK (status IN ('pending', 'running', 'retrying', 'completed', 'failed', 'cancelled'));
DROP INDEX IF EXISTS one_active_reindex_job;
CREATE UNIQUE INDEX IF NOT EXISTS uq_active_reindex_workspace
    ON reindex_jobs (COALESCE(workspace_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status IN ('pending', 'running', 'retrying');

CREATE TABLE IF NOT EXISTS audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    workspace_id UUID REFERENCES workspaces(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,
    resource_type TEXT,
    resource_id UUID,
    request_id TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_audit_events_workspace_created
    ON audit_events(workspace_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_graph_entity_name_workspace
    ON graph_entities(workspace_id, LOWER(name))
    WHERE workspace_id IS NOT NULL;
