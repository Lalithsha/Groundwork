export interface WorkspaceDto { id: string; name: string; description?: string }
export interface AuthTokensDto { accessToken: string; refreshToken: string; email: string; role: string }
export interface UploadResponseDto { message: string; filename: string; documentId: string; jobId?: string; status: string; duplicate: boolean }
export interface JobDto { id: string; status: string; progressCurrent: number; progressTotal: number; errorMessage?: string }

export interface ChangeSetDto {
  id: string; workspaceId: string; connectionId?: string; repositoryFullName: string;
  pullRequestNumber?: number; title: string; description?: string; authorLogin?: string;
  baseSha: string; headSha: string; sourceBranch?: string; targetBranch?: string;
  state: string; canonicalUrl?: string; currentAnalysisStatus: string;
  metadata: Record<string, unknown>; openedAt?: string; mergedAt?: string; updatedAt: string;
}
export interface FindingDto {
  id: string; findingKey: string; analyzerVersion: string; category: string; severity: string; statement: string;
  deterministic: boolean; evidenceStatus: string; confidence?: string;
  citations: Array<Record<string, unknown>>; details: Record<string, unknown>;
  reviewStatus: string; reviewReason?: string; reviewedAt?: string;
}
export interface PolicyEvaluationDto {
  id: string; policyVersionId: string; policyName: string; policyVersion: number;
  result: string; evidence: Array<Record<string, unknown>>; message: string; evaluatedAt: string;
}
export interface ChangeDetailDto {
  change: ChangeSetDto; findings: FindingDto[]; policies: PolicyEvaluationDto[];
  feedback: Array<Record<string, unknown>>;
}
export interface EvidenceArtifactDto {
  id: string; workspaceId: string; connectionId?: string; sourceProvider: string; externalId: string;
  artifactType: string; title: string; canonicalUrl?: string; lifecycleState: string;
  sourceAcl: Record<string, unknown>; updatedAt: string;
}
export interface EvidenceSearchHitDto {
  artifactId: string; versionId: string; artifactType: string; title: string; canonicalUrl?: string;
  sourceVersion: string; content: string; metadata: Record<string, unknown>; score: number; retrievalStage: string;
}
export interface EvidenceDetailDto {
  artifact: EvidenceArtifactDto;
  versions: Array<{ id: string; sourceVersion: string; content: string; contentHash: string; metadata: Record<string, unknown>; validFrom: string; validTo?: string }>;
  relationships: Array<{ id: string; sourceArtifactId: string; targetArtifactId: string; relationshipType: string; provenanceType: string }>;
}
export interface ConnectionDto {
  id: string; workspaceId: string; provider: string; externalAccountId: string; displayName: string;
  status: string; scopes: string[]; metadata: Record<string, unknown>; lastSyncedAt?: string; lastError?: string;
}
export interface EvidencePolicyDto {
  id: string; name: string; description?: string; activeVersion?: number; enabled: boolean;
  policyVersionId?: string; ruleType?: string; severity?: string; definition: Record<string, unknown>;
}
export interface ReleaseRecordDto {
  id: string; name: string; repositoryFullName: string; baseRef?: string; headRef: string;
  status: string; manifest: Record<string, unknown>; manifestHash: string; frozenAt: string;
}
export interface ChatResponseDto {
  answer: string; evidenceStatus: string;
  citations: Array<{ citationId: string; documentTitle: string; sectionTitle?: string; pageNumber?: number; score: number }>;
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message) }
}

class GroundworkApi {
  private readonly baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

  register(email: string, password: string) { return this.request<AuthTokensDto>('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }) }
  login(email: string, password: string) { return this.request<AuthTokensDto>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }) }
  setSession(tokens: AuthTokensDto) {
    localStorage.setItem('groundwork_access_token', tokens.accessToken);
    localStorage.setItem('groundwork_refresh_token', tokens.refreshToken);
    localStorage.setItem('groundwork_user_email', tokens.email);
  }
  clearSession() {
    ['groundwork_access_token', 'groundwork_refresh_token', 'groundwork_user_email'].forEach(key => localStorage.removeItem(key));
  }
  isAuthenticated() { return Boolean(localStorage.getItem('groundwork_access_token')) }
  currentEmail() { return localStorage.getItem('groundwork_user_email') || '' }

  workspaces() { return this.request<WorkspaceDto[]>('/api/workspaces') }
  createWorkspace(name: string, description: string) { return this.request<WorkspaceDto>('/api/workspaces', { method: 'POST', body: JSON.stringify({ name, description }) }) }
  analytics(workspaceId: string) { return this.request<Record<string, unknown>>(`/api/workspaces/${workspaceId}/analytics/summary`) }

  changes(workspaceId: string) { return this.request<ChangeSetDto[]>(`/api/workspaces/${workspaceId}/changes`) }
  change(changeId: string) { return this.request<ChangeDetailDto>(`/api/changes/${changeId}`) }
  reanalyze(changeId: string) { return this.request<Record<string, unknown>>(`/api/changes/${changeId}/reanalyze`, { method: 'POST' }) }
  evaluatePolicies(changeId: string) { return this.request<PolicyEvaluationDto[]>(`/api/changes/${changeId}/evaluate-policies`, { method: 'POST' }) }
  dryRunPolicies(changeId: string) { return this.request<Array<Record<string, unknown>>>(`/api/changes/${changeId}/policies/dry-run`, { method: 'POST' }) }
  reviewFinding(changeId: string, findingId: string, status: string, reason: string, reasonCode: string) {
    return this.request<Record<string, unknown>>(`/api/changes/${changeId}/findings/${findingId}`, {
      method: 'PATCH', body: JSON.stringify({ status, reason, reasonCode })
    });
  }

  evidence(workspaceId: string, type = '') { return this.request<EvidenceArtifactDto[]>(`/api/workspaces/${workspaceId}/evidence${this.query({ type })}`) }
  searchEvidence(workspaceId: string, query: string) { return this.request<EvidenceSearchHitDto[]>(`/api/workspaces/${workspaceId}/evidence/search${this.query({ query, limit: 20, expandGraph: true })}`) }
  evidenceDetail(workspaceId: string, artifactId: string) { return this.request<EvidenceDetailDto>(`/api/workspaces/${workspaceId}/evidence/${artifactId}`) }
  importDocuments(workspaceId: string) { return this.request<Record<string, unknown>>(`/api/workspaces/${workspaceId}/evidence/import-documents`, { method: 'POST' }) }

  connections(workspaceId: string) { return this.request<ConnectionDto[]>(`/api/workspaces/${workspaceId}/connections`) }
  createConnection(workspaceId: string, input: Record<string, unknown>) { return this.request<ConnectionDto>(`/api/workspaces/${workspaceId}/connections`, { method: 'POST', body: JSON.stringify(input) }) }
  revokeConnection(connectionId: string) { return this.request<void>(`/api/connections/${connectionId}`, { method: 'DELETE' }) }
  syncConnection(workspaceId: string, connectionId: string) { return this.request<Record<string, unknown>>(`/api/workspaces/${workspaceId}/connections/${connectionId}/sync`, { method: 'POST' }) }
  syncRuns(workspaceId: string, connectionId: string) { return this.request<Array<Record<string, unknown>>>(`/api/workspaces/${workspaceId}/connections/${connectionId}/sync-runs`) }
  atlassianAuthorize(workspaceId: string, provider: string, selectedResources: Record<string, unknown>) {
    return this.request<{ authorizationUrl: string; expiresAt: string }>(`/api/workspaces/${workspaceId}/connections/atlassian/authorize`, {
      method: 'POST', body: JSON.stringify({ provider, selectedResources })
    });
  }
  seedDemo(workspaceId: string) { return this.request<Record<string, unknown>>(`/api/workspaces/${workspaceId}/demo/evidence`, { method: 'POST' }) }

  policies(workspaceId: string) { return this.request<EvidencePolicyDto[]>(`/api/workspaces/${workspaceId}/policies`) }
  activatePolicy(workspaceId: string, policyId: string, version: number, enabled: boolean) {
    return this.request<Record<string, unknown>>(`/api/workspaces/${workspaceId}/policies/${policyId}/activation`, {
      method: 'PATCH', body: JSON.stringify({ version, enabled })
    });
  }

  releases(workspaceId: string) { return this.request<ReleaseRecordDto[]>(`/api/workspaces/${workspaceId}/releases`) }
  release(workspaceId: string, releaseId: string) { return this.request<Record<string, unknown>>(`/api/workspaces/${workspaceId}/releases/${releaseId}`) }
  createRelease(workspaceId: string, input: Record<string, unknown>) { return this.request<ReleaseRecordDto>(`/api/workspaces/${workspaceId}/releases`, { method: 'POST', body: JSON.stringify(input) }) }
  downloadRelease(workspaceId: string, releaseId: string, format: 'json' | 'html' | 'pdf') {
    const suffix = format === 'json' ? 'export' : `export.${format}`;
    return this.download(`/api/workspaces/${workspaceId}/releases/${releaseId}/${suffix}`);
  }

  documents(workspaceId: string) { return this.request<string[]>(`/api/documents${this.query({ workspaceId })}`) }
  upload(file: File, workspaceId: string) {
    const body = new FormData(); body.append('file', file);
    return this.request<UploadResponseDto>(`/api/documents/upload${this.query({ workspaceId })}`, { method: 'POST', body });
  }
  ingestionJob(jobId: string) { return this.request<JobDto>(`/api/documents/jobs/${jobId}`) }
  chat(question: string, workspaceId: string, documentFilter?: string) {
    return this.request<ChatResponseDto>('/api/chat', { method: 'POST', body: JSON.stringify({ question, retrievalMode: 'HYBRID', workspaceId, documentFilter }) });
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers);
    if (!(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    const token = localStorage.getItem('groundwork_access_token');
    if (token) headers.set('Authorization', `Bearer ${token}`);
    headers.set('X-Request-ID', crypto.randomUUID());
    let response: Response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, { ...init, headers });
      if (response.status === 401 && !path.startsWith('/api/auth/')) {
        const refreshed = await this.refreshSession();
        if (refreshed) {
          headers.set('Authorization', `Bearer ${refreshed.accessToken}`);
          response = await fetch(`${this.baseUrl}${path}`, { ...init, headers });
        }
      }
    } catch { throw new ApiError(0, 'Groundwork API is unavailable') }
    if (!response.ok) throw new ApiError(response.status, await this.errorMessage(response));
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  private async download(path: string) {
    const headers = new Headers({ 'X-Request-ID': crypto.randomUUID() });
    const token = localStorage.getItem('groundwork_access_token');
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(`${this.baseUrl}${path}`, { headers });
    if (!response.ok) throw new ApiError(response.status, await this.errorMessage(response));
    const blob = await response.blob();
    const disposition = response.headers.get('Content-Disposition') || '';
    const filename = disposition.match(/filename="([^"]+)"/)?.[1] || 'groundwork-evidence';
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a'); link.href = url; link.download = filename; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private async refreshSession(): Promise<AuthTokensDto | null> {
    const refreshToken = localStorage.getItem('groundwork_refresh_token');
    if (!refreshToken) return null;
    try {
      const response = await fetch(`${this.baseUrl}/api/auth/refresh`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken })
      });
      if (!response.ok) { this.clearSession(); return null }
      const tokens = await response.json() as AuthTokensDto; this.setSession(tokens); return tokens;
    } catch { return null }
  }
  private async errorMessage(response: Response) {
    try {
      const body = await response.json() as { message?: string; error?: string };
      return body.message || body.error || `Request failed with HTTP ${response.status}`;
    } catch { return `Request failed with HTTP ${response.status}` }
  }
  private query(values: Record<string, string | number | boolean>) {
    const params = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => { if (value !== '') params.set(key, String(value)) });
    return params.size ? `?${params}` : '';
  }
}

export const api = new GroundworkApi();
