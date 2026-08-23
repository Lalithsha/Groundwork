import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/PageState';
import { StatusPill } from '../components/StatusPill';

export function ChangesPage() {
  const { workspaceId } = useWorkspace();
  const query = useQuery({ queryKey: ['changes', workspaceId], queryFn: () => api.changes(workspaceId), enabled: !!workspaceId, refetchInterval: 3000 });
  if (query.isLoading) return <LoadingState label="Loading current head revisions…" />;
  if (query.error) return <ErrorState error={query.error} />;
  return <><PageHeader eyebrow="CHANGE QUEUE" title="Every change, with its evidence state" description="One analysis per head SHA. Superseded revisions stay historical; current revisions remain actionable." />
    {!query.data?.length ? <EmptyState title="Your change queue is empty" body="Connect a repository or load the flagship demo from Overview." action={<Link className="button secondary" to="/connections">Set up a connection</Link>} /> : <div className="table-shell"><table><thead><tr><th>Change</th><th>Repository</th><th>Scope</th><th>State</th><th>Analysis</th><th><span className="sr-only">Open</span></th></tr></thead><tbody>{query.data.map(change => { const metadata = change.metadata as { changedFiles?: number; additions?: number; deletions?: number }; return <tr key={change.id}><td><b>{change.title}</b><small>#{change.pullRequestNumber} · {change.authorLogin || 'unknown author'}</small></td><td>{change.repositoryFullName}</td><td><span className="mono">{metadata.changedFiles || 0} files</span><small className="diff">+{metadata.additions || 0} / −{metadata.deletions || 0}</small></td><td><StatusPill value={change.state} /></td><td><StatusPill value={change.currentAnalysisStatus} /></td><td><Link className="row-link" to={`/changes/${change.id}`} aria-label={`Open ${change.title}`}>→</Link></td></tr> })}</tbody></table></div>}</>;
}
