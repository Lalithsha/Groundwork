export function StatusPill({ value }: { value: string }) {
  const normalized = value.toLowerCase();
  const tone = ['pass', 'completed', 'active', 'ready', 'supported', 'present', 'verified'].some(item => normalized.includes(item))
    ? 'good' : ['fail', 'critical', 'revoked', 'missing', 'modified'].some(item => normalized.includes(item))
      ? 'bad' : ['partial', 'unknown', 'stale', 'draft', 'degraded', 'queued', 'running'].some(item => normalized.includes(item))
        ? 'warn' : 'neutral';
  return <span className={`pill ${tone}`}><span aria-hidden="true" className="pill-dot" />{value.replaceAll('_', ' ')}</span>;
}
