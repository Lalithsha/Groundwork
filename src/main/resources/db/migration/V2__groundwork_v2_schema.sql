-- Groundwork v2 Schema Migration

-- Optional workspace reference for documents
ALTER TABLE documents ADD COLUMN IF NOT EXISTS workspace_id UUID;

-- Workspaces table
CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Foreign key for documents workspace_id
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_documents_workspace'
    ) THEN
        ALTER TABLE documents ADD CONSTRAINT fk_documents_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Knowledge Artifacts table
CREATE TABLE IF NOT EXISTS knowledge_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    artifact_type TEXT NOT NULL,
    content TEXT NOT NULL,
    structured_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Document Comparisons table
CREATE TABLE IF NOT EXISTS document_comparisons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    doc_title_a TEXT NOT NULL,
    doc_title_b TEXT NOT NULL,
    comparison_result TEXT NOT NULL,
    diff_summary JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Graph Entities table
CREATE TABLE IF NOT EXISTS graph_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Graph Relationships table
CREATE TABLE IF NOT EXISTS graph_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    source_entity_id UUID NOT NULL REFERENCES graph_entities(id) ON DELETE CASCADE,
    target_entity_id UUID NOT NULL REFERENCES graph_entities(id) ON DELETE CASCADE,
    relationship_type TEXT NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Review Reports table
CREATE TABLE IF NOT EXISTS review_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    score DOUBLE PRECISION,
    feedback TEXT,
    report_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Decision Log table
CREATE TABLE IF NOT EXISTS decision_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID REFERENCES workspaces(id) ON DELETE CASCADE,
    decision TEXT NOT NULL,
    rationale TEXT,
    actor TEXT DEFAULT 'AI_REVIEWER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_documents_workspace ON documents(workspace_id);
CREATE INDEX IF NOT EXISTS idx_artifacts_workspace ON knowledge_artifacts(workspace_id);
CREATE INDEX IF NOT EXISTS idx_comparisons_workspace ON document_comparisons(workspace_id);
CREATE INDEX IF NOT EXISTS idx_graph_entities_workspace ON graph_entities(workspace_id);
CREATE INDEX IF NOT EXISTS idx_graph_rel_source ON graph_relationships(source_entity_id);
CREATE INDEX IF NOT EXISTS idx_graph_rel_target ON graph_relationships(target_entity_id);
CREATE INDEX IF NOT EXISTS idx_review_reports_workspace ON review_reports(workspace_id);
CREATE INDEX IF NOT EXISTS idx_decision_log_workspace ON decision_log(workspace_id);
