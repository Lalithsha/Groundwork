import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/PageState';
import { StatusPill } from '../components/StatusPill';

export function ReleasesPage() {
  const { workspaceId } = useWorkspace(); const client = useQueryClient(); const [name, setName] = useState('Customer API release'); const [selected, setSelected] = useState<string[]>([]);
  const releases = useQuery({ queryKey: ['releases', workspaceId], queryFn: () => api.releases(workspaceId), enabled: !!workspaceId });
  const changes = useQuery({ queryKey: ['changes', workspaceId], queryFn: () => api.changes(workspaceId), enabled: !!workspaceId });
  const create = useMutation({ mutationFn: () => {
    const chosen = (changes.data || []).filter(item => selected.includes(item.id));
    return api.createRelease(workspaceId, { name, repositoryFullName: chosen[0]?.repositoryFullName || 'multi-repository', baseRef: chosen[0]?.baseSha || '', headRef: chosen.map(item => item.headSha).join(','), changeSetIds: selected });
  }, onSuccess: async () => { setSelected([]); await client.invalidateQueries({ queryKey: ['releases', workspaceId] }) } });
  if (releases.isLoading || changes.isLoading) return <LoadingState label="Loading frozen release evidence…" />;
  if (releases.error || changes.error) return <ErrorState error={releases.error || changes.error} />;
  return <><PageHeader eyebrow="RELEASE RECORDS" title="Freeze what was known at release time" description="A canonical manifest, SHA-256 digest, policy decisions, and exact change evidence—exportable and tamper-detectable." />
    <section className="release-layout"><form className="panel release-builder" onSubmit={event => { event.preventDefault(); create.mutate() }}><p className="eyebrow">CREATE RECORD</p><h2>Select analyzed changes</h2><label>Release name<input value={name} onChange={event => setName(event.target.value)} required /></label><div className="check-list">{changes.data?.map(change => <label key={change.id}><input type="checkbox" checked={selected.includes(change.id)} onChange={event => setSelected(current => event.target.checked ? [...current, change.id] : current.filter(id => id !== change.id))} /><span><b>{change.title}</b><small>{change.repositoryFullName} · {change.headSha.slice(0, 8)}</small></span><StatusPill value={change.currentAnalysisStatus} /></label>)}</div><button className="primary wide" disabled={!selected.length || create.isPending}>{create.isPending ? 'Freezing manifest…' : `Freeze ${selected.length || ''} change${selected.length === 1 ? '' : 's'}`}</button>{create.error && <div className="inline-error" role="alert">{create.error.message}</div>}</form>
      <div className="release-list">{!releases.data?.length ? <EmptyState title="No release evidence frozen" body="Choose one or more analyzed changes to create an immutable record." /> : releases.data.map(release => <article className="panel release-card" key={release.id}><div><StatusPill value={release.status} /><span className="muted">{new Date(release.frozenAt).toLocaleString()}</span></div><h2>{release.name}</h2><p>{release.repositoryFullName}</p><code>{release.manifestHash}</code><div className="release-actions"><button className="small secondary" onClick={() => void api.downloadRelease(workspaceId, release.id, 'json')}>JSON</button><button className="small secondary" onClick={() => void api.downloadRelease(workspaceId, release.id, 'html')}>HTML</button><button className="small primary" onClick={() => void api.downloadRelease(workspaceId, release.id, 'pdf')}>PDF</button></div></article>)}</div></section></>;
}
