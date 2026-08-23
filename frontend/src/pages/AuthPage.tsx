import { useState } from 'react';
import { api } from '../api';

export function AuthPage({ onAuthenticated }: { onAuthenticated: () => void }) {
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [mode, setMode] = useState<'login' | 'register'>('login'); const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true); setError('');
    try { const tokens = mode === 'login' ? await api.login(email, password) : await api.register(email, password); api.setSession(tokens); onAuthenticated() }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Authentication failed') }
    finally { setBusy(false) }
  };
  return <main className="auth-stage"><section className="auth-story"><div className="brand light"><span className="brand-mark">G</span><div><b>Groundwork</b><small>Release intelligence</small></div></div><div><p className="eyebrow">EVIDENCE BEFORE MERGE</p><h1>Know why a change exists, what it affects, and whether it is safe.</h1><p>Groundwork connects pull requests, requirements, architecture decisions, test results, and operational history into one review-ready evidence record.</p></div><div className="story-proof"><span>Deterministic first</span><span>Source-cited AI</span><span>Immutable releases</span></div></section><section className="auth-panel"><form onSubmit={event => { event.preventDefault(); void submit() }}><p className="eyebrow">WORKSPACE ACCESS</p><h2>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2><p>Use a secure workspace identity to keep evidence tenant-scoped.</p><label>Email<input type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} /></label><label>Password<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={12} maxLength={128} required value={password} onChange={event => setPassword(event.target.value)} /></label>{error && <div className="inline-error" role="alert">{error}</div>}<button className="primary wide" disabled={busy}>{busy ? 'Working…' : mode === 'login' ? 'Sign in' : 'Create account'}</button><button type="button" className="text-button wide" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>{mode === 'login' ? 'New here? Create an account' : 'Already have an account? Sign in'}</button></form></section></main>;
}
