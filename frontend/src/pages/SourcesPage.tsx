import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type ChatResponseDto } from '../api';
import { useWorkspace } from '../app/WorkspaceContext';
import { EmptyState, ErrorState, LoadingState, PageHeader } from '../components/PageState';
import { StatusPill } from '../components/StatusPill';

export function SourcesPage() {
  const { workspaceId } = useWorkspace(); const client = useQueryClient(); const [question, setQuestion] = useState(''); const [documentFilter, setDocumentFilter] = useState(''); const [answer, setAnswer] = useState<ChatResponseDto>(); const [progress, setProgress] = useState('');
  const docs = useQuery({ queryKey: ['documents', workspaceId], queryFn: () => api.documents(workspaceId), enabled: !!workspaceId });
  const upload = useMutation({ mutationFn: async (file: File) => {
    const response = await api.upload(file, workspaceId); setProgress(response.duplicate ? 'Document already exists.' : 'Queued for durable ingestion.');
    if (response.jobId) await waitForJob(response.jobId, setProgress); return response;
  }, onSuccess: async () => { await client.invalidateQueries({ queryKey: ['documents', workspaceId] }) } });
  const chat = useMutation({ mutationFn: () => api.chat(question, workspaceId, documentFilter || undefined), onSuccess: setAnswer });
  const bridge = useMutation({ mutationFn: () => api.importDocuments(workspaceId), onSuccess: result => setProgress(`Evidence catalog updated: ${String(result.versionsCreated || 0)} version(s) created.`) });
  return <><PageHeader eyebrow="SOURCE LIBRARY" title="Documents remain a first-class evidence source" description="Upload PDF, Markdown, or text; ask grounded questions; then bridge durable documents into the temporal evidence catalog." actions={<button className="secondary" onClick={() => bridge.mutate()}>Import into evidence</button>} />
    {(progress || upload.error || bridge.error) && <div className={upload.error || bridge.error ? 'inline-error' : 'inline-notice'} role="status">{upload.error?.message || bridge.error?.message || progress}</div>}
    <section className="sources-grid"><article className="panel"><div className="section-heading"><div><p className="eyebrow">DOCUMENT CORPUS</p><h2>Workspace sources</h2></div><label className="button primary upload-button">Upload source<input type="file" accept=".pdf,.txt,.md" onChange={event => { const file = event.target.files?.[0]; if (file) upload.mutate(file) }} /></label></div>{docs.isLoading ? <LoadingState /> : docs.error ? <ErrorState error={docs.error} /> : docs.data?.length ? <div className="document-list">{docs.data.map(title => <button key={title} onClick={() => setDocumentFilter(title)} className={documentFilter === title ? 'selected' : ''}><span className="doc-icon">D</span><span><b>{title}</b><small>Durable source · workspace scoped</small></span>{documentFilter === title && <StatusPill value="SCOPED" />}</button>)}</div> : <EmptyState title="No documents uploaded" body="Add a PDF, Markdown, or text source. Extraction and embeddings run in a durable background job." />}</article>
      <article className="panel chat-panel"><p className="eyebrow">GROUNDED ASSISTANT</p><h2>Ask against verified source text</h2><p className="muted">{documentFilter ? `Scoped to ${documentFilter}` : 'Searching all workspace documents'}</p><div className="answer-space">{answer ? <><div className="answer-status"><StatusPill value={answer.evidenceStatus} /></div><p className="answer-copy">{answer.answer}</p>{answer.citations?.length > 0 && <ol className="citations">{answer.citations.map(citation => <li key={citation.citationId}><b>{citation.documentTitle}</b><span>{citation.sectionTitle || (citation.pageNumber ? `Page ${citation.pageNumber}` : 'Source passage')}</span></li>)}</ol>}</> : <EmptyState title="Ask a review question" body="Answers refuse when evidence is insufficient and keep stable source citations when grounded." />}</div><form className="chat-form" onSubmit={event => { event.preventDefault(); if (question.trim()) chat.mutate() }}><label className="sr-only" htmlFor="question">Question</label><textarea id="question" value={question} onChange={event => setQuestion(event.target.value)} placeholder="What does the rollback procedure require?" rows={3} required /><div>{documentFilter && <button type="button" className="text-button" onClick={() => setDocumentFilter('')}>Clear document scope</button>}<button className="primary" disabled={chat.isPending}>{chat.isPending ? 'Finding evidence…' : 'Ask Groundwork'}</button></div></form>{chat.error && <div className="inline-error" role="alert">{chat.error.message}</div>}</article></section></>;
}

async function waitForJob(jobId: string, update: (message: string) => void) {
  for (let attempt = 0; attempt < 90; attempt++) {
    const job = await api.ingestionJob(jobId); const total = Math.max(1, job.progressTotal || 1);
    update(`Ingestion ${job.status.toLowerCase()} · ${Math.round((job.progressCurrent / total) * 100)}%`);
    if (job.status === 'COMPLETED') return;
    if (['FAILED', 'CANCELLED'].includes(job.status)) throw new Error(job.errorMessage || `Ingestion ${job.status.toLowerCase()}`);
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Ingestion is still running; check again shortly.');
}
