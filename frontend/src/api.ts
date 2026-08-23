export interface WorkspaceDto {
  id: string;
  name: string;
  description?: string;
}

export interface CitationDto {
  citationId: string;
  chunkId: string;
  documentId?: string;
  documentTitle: string;
  sectionTitle?: string;
  pageNumber?: number;
  score: number;
}

export interface ChatResponseDto {
  answer: string;
  retrievedContexts: Array<{
    id: string;
    documentId?: string;
    title: string;
    content: string;
    sourceType: string;
    score: number;
    sectionTitle?: string;
    pageNumber?: number;
  }>;
  citations: CitationDto[];
  evidenceStatus: 'GROUNDED' | 'INSUFFICIENT' | 'UNKNOWN';
  requestId?: string;
}

export interface UploadResponseDto {
  message: string;
  filename: string;
  documentId: string;
  jobId?: string;
  status: string;
  duplicate: boolean;
}

export interface JobDto {
  id: string;
  status: string;
  progressCurrent: number;
  progressTotal: number;
  errorMessage?: string;
}

export interface KnowledgeArtifactDto {
  id: string;
  workspaceId?: string;
  title: string;
  artifactType: string;
  content: string;
  structuredData: string;
}

export interface GraphDto {
  entities: Array<{ id: string; name: string; entityType: string; description?: string }>;
  relationships: Array<{
    id: string;
    sourceEntityId: string;
    targetEntityId: string;
    relationshipType: string;
    description?: string;
  }>;
}

export interface AuthTokensDto {
  accessToken: string;
  refreshToken: string;
  email: string;
  role: string;
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

class GroundworkApi {
  private readonly baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

  async register(email: string, password: string): Promise<AuthTokensDto> {
    return this.request('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) });
  }

  async login(email: string, password: string): Promise<AuthTokensDto> {
    return this.request('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
  }

  setSession(tokens: AuthTokensDto): void {
    localStorage.setItem('groundwork_access_token', tokens.accessToken);
    localStorage.setItem('groundwork_refresh_token', tokens.refreshToken);
    localStorage.setItem('groundwork_user_email', tokens.email);
  }

  clearSession(): void {
    localStorage.removeItem('groundwork_access_token');
    localStorage.removeItem('groundwork_refresh_token');
    localStorage.removeItem('groundwork_user_email');
  }

  isAuthenticated(): boolean {
    return Boolean(localStorage.getItem('groundwork_access_token'));
  }

  async workspaces(): Promise<WorkspaceDto[]> {
    return this.request('/api/workspaces');
  }

  async createWorkspace(name: string, description: string): Promise<WorkspaceDto> {
    return this.request('/api/workspaces', { method: 'POST', body: JSON.stringify({ name, description }) });
  }

  async documents(workspaceId: string): Promise<string[]> {
    return this.request(`/api/documents${this.query({ workspaceId })}`);
  }

  async deleteDocument(title: string, workspaceId: string): Promise<void> {
    await this.request(`/api/documents${this.query({ title, workspaceId })}`, { method: 'DELETE' });
  }

  async upload(file: File, workspaceId: string): Promise<UploadResponseDto> {
    const body = new FormData();
    body.append('file', file);
    return this.request(`/api/documents/upload${this.query({ workspaceId })}`, { method: 'POST', body });
  }

  async ingestionJob(jobId: string): Promise<JobDto> {
    return this.request(`/api/documents/jobs/${encodeURIComponent(jobId)}`);
  }

  async chat(question: string, retrievalMode: string, workspaceId: string, documentFilter?: string): Promise<ChatResponseDto> {
    return this.request('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ question, retrievalMode, workspaceId, documentFilter })
    });
  }

  async reindex(workspaceId: string): Promise<JobDto> {
    return this.request(`/api/admin/reindex${this.query({ workspaceId })}`, { method: 'POST' });
  }

  async compare(docTitleA: string, docTitleB: string, workspaceId: string): Promise<unknown> {
    return this.request('/api/compare', {
      method: 'POST', body: JSON.stringify({ workspaceId, docTitleA, docTitleB })
    });
  }

  async review(documentTitle: string, workspaceId: string): Promise<unknown> {
    return this.request('/api/review', {
      method: 'POST', body: JSON.stringify({ workspaceId, documentTitle })
    });
  }

  async artifacts(workspaceId: string): Promise<KnowledgeArtifactDto[]> {
    return this.request(`/api/artifacts${this.query({ workspaceId })}`);
  }

  async extractArtifact(documentTitle: string, artifactType: string, workspaceId: string): Promise<KnowledgeArtifactDto> {
    return this.request('/api/artifacts/extract', {
      method: 'POST', body: JSON.stringify({ workspaceId, documentTitle, artifactType })
    });
  }

  async graph(workspaceId: string): Promise<GraphDto> {
    return this.request(`/api/graph${this.query({ workspaceId })}`);
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const token = localStorage.getItem('groundwork_access_token');
    const headers = new Headers(init.headers);
    if (!(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
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
    } catch {
      throw new ApiError(0, 'Groundwork API is unavailable');
    }
    if (!response.ok) {
      let message = `Request failed with HTTP ${response.status}`;
      try {
        const body = await response.json();
        if (typeof body.message === 'string') message = body.message;
        else if (typeof body.error === 'string') message = body.error;
      } catch {
        // Preserve the status-based message for non-JSON errors.
      }
      if (response.status === 401 && !path.startsWith('/api/auth/')) {
        window.dispatchEvent(new CustomEvent('groundwork:unauthorized', { detail: message }));
      }
      throw new ApiError(response.status, message);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }

  private async refreshSession(): Promise<AuthTokensDto | null> {
    const refreshToken = localStorage.getItem('groundwork_refresh_token');
    if (!refreshToken) return null;
    try {
      const response = await fetch(`${this.baseUrl}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Request-ID': crypto.randomUUID() },
        body: JSON.stringify({ refreshToken })
      });
      if (!response.ok) {
        this.clearSession();
        return null;
      }
      const tokens = await response.json() as AuthTokensDto;
      this.setSession(tokens);
      return tokens;
    } catch {
      return null;
    }
  }

  private query(values: Record<string, string>): string {
    const params = new URLSearchParams();
    Object.entries(values).forEach(([key, value]) => { if (value) params.set(key, value); });
    const encoded = params.toString();
    return encoded ? `?${encoded}` : '';
  }
}

export const api = new GroundworkApi();
