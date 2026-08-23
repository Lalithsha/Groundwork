import type { ReactNode } from 'react';

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow: string; title: string; description: string; actions?: ReactNode }) {
  return <header className="page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{description}</p></div>{actions && <div className="header-actions">{actions}</div>}</header>;
}
export function LoadingState({ label = 'Loading workspace data…' }: { label?: string }) { return <div className="state-card" role="status"><span className="spinner" />{label}</div> }
export function ErrorState({ error }: { error: unknown }) { return <div className="state-card error" role="alert"><b>Something needs attention.</b><span>{error instanceof Error ? error.message : 'The request could not be completed.'}</span></div> }
export function EmptyState({ title, body, action }: { title: string; body: string; action?: ReactNode }) { return <div className="empty-state"><span className="empty-mark">◎</span><h2>{title}</h2><p>{body}</p>{action}</div> }
