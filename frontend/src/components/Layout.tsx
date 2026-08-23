import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { api } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { ErrorState, LoadingState } from './PageState';

const links = [
  ['/', 'Overview', '01'], ['/changes', 'Change queue', '02'], ['/evidence', 'Evidence', '03'],
  ['/policies', 'Policies', '04'], ['/releases', 'Releases', '05'], ['/connections', 'Connections', '06'],
  ['/sources', 'Sources & chat', '07']
];

export function Layout({ onSignOut }: { onSignOut: () => void }) {
  const workspace = useWorkspace();
  const [name, setName] = useState('Engineering');
  if (workspace.loading) return <main className="center-stage"><LoadingState /></main>;
  if (workspace.error) return <main className="center-stage"><ErrorState error={workspace.error} /></main>;
  if (!workspace.workspaces.length) return <main className="center-stage"><form className="onboarding-card" onSubmit={async event => { event.preventDefault(); await workspace.createWorkspace(name, 'Engineering evidence workspace') }}><p className="eyebrow">FIRST WORKSPACE</p><h1>Create your evidence workspace</h1><p>Connections, changes, policies, and release records are isolated by workspace.</p><label>Workspace name<input value={name} onChange={event => setName(event.target.value)} minLength={2} required /></label><button className="primary" type="submit">Create workspace</button></form></main>;
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">G</span><div><b>Groundwork</b><small>Release intelligence</small></div></div>
      <nav aria-label="Primary navigation">{links.map(([to, label, number]) => <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => isActive ? 'active' : ''}><span>{number}</span>{label}</NavLink>)}</nav>
      <div className="sidebar-foot"><div className="signal"><span className="live-dot" /> Evidence pipeline online</div><button className="text-button" onClick={onSignOut}>Sign out · {api.currentEmail()}</button></div>
    </aside>
    <section className="workspace-shell">
      <header className="topbar"><div><span className="muted">Workspace</span><select aria-label="Active workspace" value={workspace.workspaceId} onChange={event => workspace.setWorkspaceId(event.target.value)}>{workspace.workspaces.map(item => <option value={item.id} key={item.id}>{item.name}</option>)}</select></div><div className="topbar-context"><span className="kbd">⌘ K</span><span className="avatar">{api.currentEmail().slice(0, 2).toUpperCase()}</span></div></header>
      <main id="main-content" className="content" tabIndex={-1}><Outlet /></main>
    </section>
  </div>;
}
