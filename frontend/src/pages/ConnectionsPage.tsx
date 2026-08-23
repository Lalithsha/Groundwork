import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type ConnectionDto } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/PageState';
import { StatusPill } from '../components/StatusPill';

export function ConnectionsPage() {
  const { workspaceId } = useWorkspace(); const client = useQueryClient(); const [provider, setProvider] = useState('JIRA'); const [resources, setResources] = useState('PROJ');
  const [installationId, setInstallationId] = useState(''); const [repository, setRepository] = useState('');
  const query = useQuery({ queryKey: ['connections', workspaceId], queryFn: () => api.connections(workspaceId), enabled: !!workspaceId });
  const refresh = () => client.invalidateQueries({ queryKey: ['connections', workspaceId] });
  const seed = useMutation({ mutationFn: () => api.seedDemo(workspaceId), onSuccess: async () => { await new Promise(resolve => setTimeout(resolve, 1200)); await refresh() } });
  const github = useMutation({ mutationFn: () => api.createConnection(workspaceId, { provider: 'GITHUB', externalAccountId: installationId, displayName: repository || `GitHub installation ${installationId}`, scopes: ['contents:read', 'pull_requests:read', 'checks:write'], metadata: { repository } }), onSuccess: refresh });
  const authorize = useMutation({ mutationFn: async () => {
    const values = resources.split(',').map(item => item.trim()).filter(Boolean);
    const selectedResources = provider === 'JIRA' ? { projectKeys: values } : { spaceIds: values };
    return api.atlassianAuthorize(workspaceId, provider, selectedResources);
  }, onSuccess: result => { window.location.assign(result.authorizationUrl) } });
  const revoke = useMutation({ mutationFn: (id: string) => api.revokeConnection(id), onSuccess: refresh });
  const sync = useMutation({ mutationFn: (id: string) => api.syncConnection(workspaceId, id), onSuccess: refresh });
  if (query.isLoading) return <LoadingState label="Checking connector health…" />;
  if (query.error) return <ErrorState error={query.error} />;
  const error = seed.error || github.error || authorize.error || revoke.error || sync.error;
  return <><PageHeader eyebrow="CONNECTIONS" title="Bring intent, code, and operations together" description="Credentials are encrypted at rest. Synchronization is selected-scope, versioned, recoverable, and revocation-aware." actions={<button className="primary" onClick={() => seed.mutate()} disabled={seed.isPending}>Load demo sources</button>} />
    {error && <div className="inline-error" role="alert">{error.message}</div>}
    <section className="setup-grid"><form className="panel setup-card" onSubmit={event => { event.preventDefault(); github.mutate() }}><span className="provider-logo github">GH</span><div><p className="eyebrow">GITHUB APP</p><h2>Register an installation</h2><p>For local or self-hosted GitHub App setup. Webhooks remain HMAC-verified and deduplicated.</p></div><label>Installation ID<input value={installationId} onChange={event => setInstallationId(event.target.value)} required placeholder="12345678" /></label><label>Repository or account label<input value={repository} onChange={event => setRepository(event.target.value)} required placeholder="acme/payments-api" /></label><button className="secondary">Register installation</button></form>
      <form className="panel setup-card" onSubmit={event => { event.preventDefault(); authorize.mutate() }}><span className="provider-logo atlassian">A</span><div><p className="eyebrow">ATLASSIAN OAUTH 2.0</p><h2>Connect selected knowledge</h2><p>Sync only the Jira projects or Confluence spaces the review workflow needs.</p></div><label>Product<select value={provider} onChange={event => setProvider(event.target.value)}><option value="JIRA">Jira projects</option><option value="CONFLUENCE">Confluence spaces</option></select></label><label>{provider === 'JIRA' ? 'Project keys' : 'Space IDs'}<input value={resources} onChange={event => setResources(event.target.value)} placeholder="Comma-separated" required /></label><button className="secondary">Continue with Atlassian</button></form></section>
    <section className="panel"><div className="section-heading"><div><p className="eyebrow">CONNECTION HEALTH</p><h2>Active evidence sources</h2></div><span className="muted">{query.data?.length || 0} configured</span></div>{!query.data?.length ? <EmptyState title="No sources connected" body="Use a configured OAuth provider, register a GitHub installation, or load the safe demo sources." /> : <div className="connection-list">{query.data.map(connection => <ConnectionRow key={connection.id} connection={connection} onSync={() => sync.mutate(connection.id)} onRevoke={() => revoke.mutate(connection.id)} />)}</div>}</section></>;
}

function ConnectionRow({ connection, onSync, onRevoke }: { connection: ConnectionDto; onSync: () => void; onRevoke: () => void }) {
  const demo = connection.metadata.demo === true;
  return <article className="connection-row"><span className={`provider-logo ${connection.provider.toLowerCase()}`}>{connection.provider.slice(0, 2)}</span><div><h3>{connection.displayName}</h3><p>{connection.provider} · {connection.scopes.join(', ') || 'workspace scoped'}</p>{connection.lastError && <small className="danger-text">{connection.lastError}</small>}</div><div className="connection-status"><StatusPill value={connection.status} /><small>{connection.lastSyncedAt ? `Synced ${new Date(connection.lastSyncedAt).toLocaleString()}` : demo ? 'Seeded source' : 'Not yet synchronized'}</small></div><div className="row-actions">{['JIRA', 'CONFLUENCE'].includes(connection.provider) && !demo && <button className="small secondary" onClick={onSync}>Sync now</button>}<button className="small text-button danger-text" onClick={onRevoke}>Revoke</button></div></article>;
}
