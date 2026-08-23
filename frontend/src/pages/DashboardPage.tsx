import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/PageState';
import { StatusPill } from '../components/StatusPill';

export function DashboardPage() {
  const { workspaceId } = useWorkspace(); const client = useQueryClient();
  const analytics = useQuery({ queryKey: ['analytics', workspaceId], queryFn: () => api.analytics(workspaceId), enabled: !!workspaceId });
  const changes = useQuery({ queryKey: ['changes', workspaceId], queryFn: () => api.changes(workspaceId), enabled: !!workspaceId, refetchInterval: 3000 });
  const seed = useMutation({ mutationFn: () => api.seedDemo(workspaceId), onSuccess: async () => { await new Promise(resolve => setTimeout(resolve, 1800)); await client.invalidateQueries() } });
  if (analytics.isLoading || changes.isLoading) return <LoadingState label="Assembling release evidence…" />;
  if (analytics.error || changes.error) return <ErrorState error={analytics.error || changes.error} />;
  const metrics = analytics.data || {}; const queue = changes.data || [];
  const metricCards: Array<[string, string | number, string]> = [
    ['Active connections', Number(metrics.connections || 0), 'Sources feeding this workspace'],
    ['Analyzed changes', Number(metrics.analyzedChanges || 0), 'Current head revisions assessed'],
    ['Open evidence gaps', Number(metrics.openEvidenceGaps || 0), 'Deterministic actions before merge'],
    ['Release records', Number(metrics.releaseRecords || 0), 'Frozen, verifiable manifests']
  ];
  return <><PageHeader eyebrow="COMMAND CENTER" title="Release confidence, at a glance" description="The review context your team would otherwise reconstruct across five tools." actions={<button className="primary" onClick={() => seed.mutate()} disabled={seed.isPending}>{seed.isPending ? 'Seeding scenario…' : 'Load flagship demo'}</button>} />
    <section className="metric-grid" aria-label="Workspace metrics">{metricCards.map(([label, value, hint]) => <article className="metric-card" key={label}><span>{label}</span><strong>{String(value).padStart(2, '0')}</strong><small>{hint}</small></article>)}</section>
    <section className="dashboard-grid"><article className="panel"><div className="section-heading"><div><p className="eyebrow">RECENT CHANGES</p><h2>What needs a reviewer</h2></div><Link to="/changes">View queue →</Link></div>{queue.length ? <div className="change-list">{queue.slice(0, 5).map(change => <Link className="change-row" to={`/changes/${change.id}`} key={change.id}><div><span className="repo-label">{change.repositoryFullName} · PR #{change.pullRequestNumber}</span><b>{change.title}</b><small>{change.sourceBranch} → {change.targetBranch}</small></div><StatusPill value={change.currentAnalysisStatus} /></Link>)}</div> : <EmptyState title="No changes analyzed yet" body="Load the flagship demo or connect GitHub to turn a pull request into an evidence report." />}</article>
    <aside className="panel signal-panel"><p className="eyebrow">WHY THIS EXISTS</p><h2>One answer to four review questions.</h2><ol><li><span>01</span><div><b>Why does this change exist?</b><p>Linked intent and acceptance criteria.</p></div></li><li><span>02</span><div><b>What does it affect?</b><p>Code paths, contracts, ownership, and history.</p></div></li><li><span>03</span><div><b>Is it safe?</b><p>Checks, approvals, rollback, and policy evidence.</p></div></li><li><span>04</span><div><b>What is still missing?</b><p>Explainable actions, never an opaque score.</p></div></li></ol></aside></section>
    {seed.error && <div className="inline-error" role="alert">{seed.error.message}</div>}</>;
}
