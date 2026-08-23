import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/PageState';
import { StatusPill } from '../components/StatusPill';

export function PoliciesPage() {
  const { workspaceId } = useWorkspace(); const client = useQueryClient();
  const query = useQuery({ queryKey: ['policies', workspaceId], queryFn: () => api.policies(workspaceId), enabled: !!workspaceId });
  const toggle = useMutation({ mutationFn: ({ id, version, enabled }: { id: string; version: number; enabled: boolean }) => api.activatePolicy(workspaceId, id, version, enabled), onSuccess: () => client.invalidateQueries({ queryKey: ['policies', workspaceId] }) });
  if (query.isLoading) return <LoadingState label="Loading versioned policies…" />;
  if (query.error) return <ErrorState error={query.error} />;
  return <><PageHeader eyebrow="POLICY ENGINE" title="Explainable controls, not a black-box score" description="Only deterministic findings can block a release. Every decision identifies its exact rule version and evidence." />
    {toggle.error && <div className="inline-error" role="alert">{toggle.error.message}</div>}
    {!query.data?.length ? <EmptyState title="No policies configured" body="Default release-evidence policies are created when this page opens." /> : <div className="policy-grid">{query.data.map(policy => <article className="panel policy-card" key={policy.id}><div className="policy-card-head"><span className="policy-icon">{policy.enabled ? '✓' : '○'}</span><StatusPill value={policy.enabled ? 'ACTIVE' : 'DRY RUN'} /></div><h2>{policy.name}</h2><p>{policy.description}</p><dl><div><dt>Rule</dt><dd>{policy.ruleType?.replaceAll('_', ' ')}</dd></div><div><dt>Severity</dt><dd>{policy.severity}</dd></div><div><dt>Version</dt><dd>{policy.activeVersion || 1}</dd></div><div><dt>Finding</dt><dd>{String(policy.definition.findingCategory || '—')}</dd></div></dl><button className="secondary wide" disabled={toggle.isPending} onClick={() => toggle.mutate({ id: policy.id, version: policy.activeVersion || 1, enabled: !policy.enabled })}>{policy.enabled ? 'Disable policy' : 'Activate version'}</button></article>)}</div>}
    <aside className="callout"><span>i</span><div><b>Safe activation workflow</b><p>Run a dry preview on a Change Detail page, inspect exact evidence, then activate the desired immutable version. Time-bounded exceptions require an administrator, rationale, and expiry.</p></div></aside></>;
}
