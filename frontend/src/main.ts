import * as d3 from 'd3';
import { api, ApiError, type KnowledgeArtifactDto } from './api';

// DOM Element Selectors
const messageList = document.getElementById('messageList') as HTMLDivElement;
const chatForm = document.getElementById('chatForm') as HTMLFormElement;
const userInput = document.getElementById('userInput') as HTMLInputElement;
const modeSelect = document.getElementById('retrievalModeSelect') as HTMLSelectElement;
const reindexBtn = document.getElementById('reindexBtn') as HTMLButtonElement;
const attachBtn = document.getElementById('attachBtn') as HTMLButtonElement;
const fileInput = document.getElementById('fileInput') as HTMLInputElement;
const dropZoneOverlay = document.getElementById('dropZoneOverlay') as HTMLDivElement;
const corpusList = document.getElementById('corpusList') as HTMLDivElement;
const corpusCountBadge = document.getElementById('corpusCountBadge') as HTMLSpanElement;
const mentionDropdown = document.getElementById('mentionDropdown') as HTMLDivElement;
const toastContainer = document.getElementById('toastContainer') as HTMLDivElement;
const confirmModal = document.getElementById('confirmModal') as HTMLDivElement;
const modalTitle = document.getElementById('modalTitle') as HTMLHeadingElement;
const modalMessage = document.getElementById('modalMessage') as HTMLDivElement;
const modalCancelBtn = document.getElementById('modalCancelBtn') as HTMLButtonElement;
const modalConfirmBtn = document.getElementById('modalConfirmBtn') as HTMLButtonElement;
const dashboardCorpusCount = document.getElementById('dashboardCorpusCount') as HTMLSpanElement;
const dashboardArtifactCount = document.getElementById('dashboardArtifactCount') as HTMLSpanElement;
const authModal = document.getElementById('authModal') as HTMLDivElement;
const authForm = document.getElementById('authForm') as HTMLFormElement;
const authEmail = document.getElementById('authEmail') as HTMLInputElement;
const authPassword = document.getElementById('authPassword') as HTMLInputElement;
const authError = document.getElementById('authError') as HTMLParagraphElement;
const registerBtn = document.getElementById('registerBtn') as HTMLButtonElement;
const accountBtn = document.getElementById('accountBtn') as HTMLButtonElement;

// Workspace Selectors
const workspaceSelect = document.getElementById('workspaceSelect') as HTMLSelectElement;
const activeWorkspaceBadge = document.getElementById('activeWorkspaceBadge') as HTMLSpanElement;
const currentWorkspaceName = document.getElementById('currentWorkspaceName') as HTMLSpanElement;

// Tab View Selectors
const tabButtons = document.querySelectorAll('.tab-btn');
const tabViews = document.querySelectorAll('.tab-view');

// Document Intelligence Selectors
const artifactsGrid = document.getElementById('artifactsGrid') as HTMLDivElement;
const artifactSearchInput = document.getElementById('artifactSearchInput') as HTMLInputElement;
const extractArtifactsBtn = document.getElementById('extractArtifactsBtn') as HTMLButtonElement;
const artifactCatBtns = document.querySelectorAll('.artifact-cat-btn');

// Artifact Detail Modal
const artifactDetailModal = document.getElementById('artifactDetailModal') as HTMLDivElement;
const artifactModalIcon = document.getElementById('artifactModalIcon') as HTMLDivElement;
const artifactModalCategory = document.getElementById('artifactModalCategory') as HTMLSpanElement;
const artifactModalTitle = document.getElementById('artifactModalTitle') as HTMLHeadingElement;
const artifactModalBody = document.getElementById('artifactModalBody') as HTMLDivElement;
const artifactModalCloseBtn = document.getElementById('artifactModalCloseBtn') as HTMLButtonElement;

// Comparison Studio Selectors
const compareDocA = document.getElementById('compareDocA') as HTMLSelectElement;
const compareDocB = document.getElementById('compareDocB') as HTMLSelectElement;
const compareMode = document.getElementById('compareMode') as HTMLSelectElement;
const runCompareBtn = document.getElementById('runCompareBtn') as HTMLButtonElement;
const compareResults = document.getElementById('compareResults') as HTMLDivElement;

// AI Senior Reviewer Selectors
const reviewDocSelect = document.getElementById('reviewDocSelect') as HTMLSelectElement;
const reviewFocusSelect = document.getElementById('reviewFocusSelect') as HTMLSelectElement;
const runReviewBtn = document.getElementById('runReviewBtn') as HTMLButtonElement;
const reviewResults = document.getElementById('reviewResults') as HTMLDivElement;

// D3 Knowledge Graph Selectors
const graphNodeSearch = document.getElementById('graphNodeSearch') as HTMLInputElement;
const graphZoomInBtn = document.getElementById('graphZoomInBtn') as HTMLButtonElement;
const graphZoomOutBtn = document.getElementById('graphZoomOutBtn') as HTMLButtonElement;
const graphResetBtn = document.getElementById('graphResetBtn') as HTMLButtonElement;
const refreshGraphBtn = document.getElementById('refreshGraphBtn') as HTMLButtonElement;
const graphFilterBtns = document.querySelectorAll('.graph-filter-btn');
const nodeDetailDrawer = document.getElementById('nodeDetailDrawer') as HTMLDivElement;
const drawerNodeBadge = document.getElementById('drawerNodeBadge') as HTMLSpanElement;
const drawerNodeTitle = document.getElementById('drawerNodeTitle') as HTMLHeadingElement;
const drawerNodeBody = document.getElementById('drawerNodeBody') as HTMLDivElement;
const closeDrawerBtn = document.getElementById('closeDrawerBtn') as HTMLButtonElement;

// Data Interfaces
interface DocumentChunk {
  id: string;
  title: string;
  content: string;
  sourceType: string;
  score: number;
}

interface Artifact {
  id: string;
  category: 'requirements' | 'apis' | 'risks' | 'decisions';
  title: string;
  summary: string;
  tags: string[];
  docSource: string;
  detail: string;
  severityOrPriority: 'High' | 'Medium' | 'Low' | 'Critical';
}

interface GraphNode extends d3.SimulationNodeDatum {
  id: string;
  label: string;
  type: 'Document' | 'Chunk' | 'Entity' | 'API' | 'Risk' | 'Decision';
  docSource?: string;
  properties?: Record<string, string>;
}

interface GraphLink extends d3.SimulationLinkDatum<GraphNode> {
  source: string | GraphNode;
  target: string | GraphNode;
  relationship: string;
}

// Global State
let activeCorpusDocs: string[] = [];
let selectedMentionIdx = 0;
let currentTab = 'chat';
let activeWorkspace = '';
let currentArtifactCatFilter = 'all';
let intelligenceArtifacts: Artifact[] = [];
let workspaceListenerInitialized = false;
let selectedDocumentFilter: string | undefined;

// Helper: Toast Notifications
function showToast(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info') {
  if (!toastContainer) return;
  const toast = document.createElement('div');
  const borderClass = type === 'success' ? 'border-l-4 border-l-emerald-500 border-slate-800' :
                     (type === 'error' ? 'border-l-4 border-l-rose-500 border-slate-800' :
                     (type === 'warning' ? 'border-l-4 border-l-amber-500 border-slate-800' : 'border-l-4 border-l-indigo-500 border-slate-800'));

  toast.className = `bg-slate-900 border text-slate-100 p-3 px-4 rounded-xl shadow-2xl backdrop-blur-xl text-xs flex items-center gap-2 animate-toast-slide ${borderClass}`;
  const icon = type === 'success' ? '✅' : (type === 'error' ? '⚠️' : (type === 'warning' ? '🔔' : 'ℹ️'));
  const iconElement = document.createElement('span');
  iconElement.textContent = icon;
  const messageElement = document.createElement('span');
  messageElement.textContent = message;
  toast.append(iconElement, messageElement);
  
  toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(30px)';
    toast.style.transition = 'all 0.3s cubic-bezier(0.16, 1, 0.3, 1)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

function initAuth() {
  const show = (message = '') => {
    authError.textContent = message;
    authError.classList.toggle('hidden', !message);
    authModal.classList.remove('hidden');
    authEmail.focus();
  };
  const submit = async (register: boolean) => {
    authError.classList.add('hidden');
    try {
      const tokens = register
        ? await api.register(authEmail.value, authPassword.value)
        : await api.login(authEmail.value, authPassword.value);
      api.setSession(tokens);
      authModal.classList.add('hidden');
      accountBtn.textContent = tokens.email;
      await initWorkspace();
      await fetchCorpusDocs();
      showToast(register ? 'Account created' : 'Signed in', 'success');
    } catch (error) {
      show(errorMessage(error));
    }
  };
  authForm.addEventListener('submit', event => { event.preventDefault(); void submit(false); });
  registerBtn.addEventListener('click', () => void submit(true));
  accountBtn.textContent = localStorage.getItem('groundwork_user_email') || 'Sign in';
  accountBtn.addEventListener('click', () => {
    if (api.isAuthenticated()) {
      api.clearSession();
      activeWorkspace = '';
      accountBtn.textContent = 'Sign in';
      showToast('Signed out', 'info');
    }
    show();
  });
  window.addEventListener('groundwork:unauthorized', event => {
    const message = event instanceof CustomEvent && typeof event.detail === 'string' ? event.detail : 'Please sign in';
    show(message);
  });
  return { show };
}

// Helper: Confirmation Modal
function showConfirmModal(title: string, message: string): Promise<boolean> {
  return new Promise((resolve) => {
    if (!confirmModal || !modalTitle || !modalMessage || !modalCancelBtn || !modalConfirmBtn) {
      resolve(confirm(`${title}\n${message}`));
      return;
    }

    modalTitle.innerText = title;
    modalMessage.innerText = message;
    confirmModal.classList.remove('hidden');

    const handleConfirm = () => {
      cleanup();
      confirmModal.classList.add('hidden');
      resolve(true);
    };

    const handleCancel = () => {
      cleanup();
      confirmModal.classList.add('hidden');
      resolve(false);
    };

    const cleanup = () => {
      modalConfirmBtn.removeEventListener('click', handleConfirm);
      modalCancelBtn.removeEventListener('click', handleCancel);
    };

    modalConfirmBtn.addEventListener('click', handleConfirm);
    modalCancelBtn.addEventListener('click', handleCancel);
  });
}

// -------------------------------------------------------------
// TAB SWITCHING NAVIGATION
// -------------------------------------------------------------
function initTabs() {
  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const tabName = btn.getAttribute('data-tab');
      if (tabName) switchTab(tabName);
    });
  });
}

function switchTab(tabName: string) {
  currentTab = tabName;

  const viewHeadingTitle = document.getElementById('viewHeadingTitle');
  const viewHeadingDesc = document.getElementById('viewHeadingDesc');

  tabButtons.forEach(btn => {
    const isTarget = btn.getAttribute('data-tab') === tabName;
    if (isTarget) {
      btn.className = 'tab-btn flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-bold transition-all bg-gradient-to-r from-indigo-600 to-indigo-500 text-white shadow-lg shadow-indigo-600/30 border border-indigo-500/40';
    } else {
      btn.className = 'tab-btn flex items-center justify-between px-3.5 py-2.5 rounded-xl text-xs font-semibold text-slate-400 hover:text-slate-100 hover:bg-slate-900/80 transition-all border border-transparent hover:border-slate-800';
    }
  });

  tabViews.forEach(view => {
    if (view.id === `view-${tabName}`) {
      view.classList.remove('hidden');
      if (view.id === 'view-chat') view.classList.add('flex');
    } else {
      view.classList.add('hidden');
      view.classList.remove('flex');
    }
  });

  if (viewHeadingTitle && viewHeadingDesc) {
    if (tabName === 'chat') {
      viewHeadingTitle.innerText = 'Executive Knowledge Hub';
      viewHeadingDesc.innerText = 'Vector RAG query engine, document intelligence, multi-doc diff studio, and knowledge graph';
    } else if (tabName === 'intelligence') {
      viewHeadingTitle.innerText = 'Document Intelligence Artifacts';
      viewHeadingDesc.innerText = 'Extracted functional requirements, API contracts, system risks, and architecture decisions (ADRs)';
    } else if (tabName === 'compare') {
      viewHeadingTitle.innerText = 'Multi-Document Comparison Diff Studio';
      viewHeadingDesc.innerText = 'Compare requirements, API specifications, and structural changes across documents with AI synthesis';
    } else if (tabName === 'review') {
      viewHeadingTitle.innerText = 'AI Senior Reviewer Findings Report';
      viewHeadingDesc.innerText = 'Automated architectural compliance, security vulnerability, and code style quality audit';
    } else if (tabName === 'graph') {
      viewHeadingTitle.innerText = 'D3 Force Knowledge Graph Explorer';
      viewHeadingDesc.innerText = 'Dynamic 2D force graph visualizing documents, text chunks, concept entities, and API relationships';
    }
  }

  // Lazy render triggers
  if (tabName === 'intelligence') {
    void loadArtifacts();
  } else if (tabName === 'compare') {
    populateCompareDropdowns();
  } else if (tabName === 'review') {
    populateReviewDropdowns();
  } else if (tabName === 'graph') {
    renderKnowledgeGraph();
  }
}

// -------------------------------------------------------------
// WORKSPACE SELECTION & CORPUS MANAGEMENT
// -------------------------------------------------------------
async function initWorkspace() {
  try {
    let workspaces = await api.workspaces();
    if (workspaces.length === 0) {
      workspaces = [await api.createWorkspace('Groundwork Core', 'Default document intelligence workspace')];
    }
    workspaceSelect.innerHTML = '';
    workspaces.forEach(workspace => {
      const option = document.createElement('option');
      option.value = workspace.id;
      option.textContent = `📁 ${workspace.name}`;
      workspaceSelect.appendChild(option);
    });
    activeWorkspace = workspaces[0].id;
    if (currentWorkspaceName) currentWorkspaceName.innerText = workspaces[0].name;
  } catch (error) {
    showToast(errorMessage(error), 'error');
  }

  if (workspaceSelect && !workspaceListenerInitialized) {
    workspaceListenerInitialized = true;
    workspaceSelect.addEventListener('change', () => {
      activeWorkspace = workspaceSelect.value;
      const selectedOption = workspaceSelect.options[workspaceSelect.selectedIndex].text;
      
      if (currentWorkspaceName) currentWorkspaceName.innerText = selectedOption.replace(/^[^\s]+\s/, '');
      if (activeWorkspaceBadge) {
        activeWorkspaceBadge.innerText = activeWorkspace.includes('sec') ? 'SEC' : (activeWorkspace.includes('fin') ? 'FIN' : 'PROD');
      }

      fetchCorpusDocs();
      if (currentTab === 'intelligence') renderArtifactsGrid();
      if (currentTab === 'graph') renderKnowledgeGraph();
    });
  }
}

async function fetchCorpusDocs() {
  if (!activeWorkspace) {
    activeCorpusDocs = [];
    renderCorpusList();
    return;
  }
  try {
    activeCorpusDocs = await api.documents(activeWorkspace);
  } catch (error) {
    activeCorpusDocs = [];
    showToast(errorMessage(error), 'error');
  }

  renderCorpusList();
  populateCompareDropdowns();
  populateReviewDropdowns();
}

function renderCorpusList() {
  if (!corpusList) return;
  corpusList.innerHTML = '';

  if (corpusCountBadge) corpusCountBadge.innerText = `${activeCorpusDocs.length} files`;
  if (dashboardCorpusCount) dashboardCorpusCount.innerText = `${activeCorpusDocs.length} ${activeCorpusDocs.length === 1 ? 'File' : 'Files'}`;

  if (activeCorpusDocs.length === 0) {
    corpusList.innerHTML = '<div class="text-xs text-slate-500 p-1">No documents uploaded yet</div>';
    return;
  }

  activeCorpusDocs.forEach((docTitle) => {
    const item = document.createElement('div');
    item.className = 'flex items-center justify-between gap-2 p-2 px-3 rounded-xl bg-slate-800/40 border border-slate-800/60 hover:border-indigo-500/30 hover:bg-slate-800/80 transition-all text-xs text-slate-300 group';
    item.innerHTML = `
      <div class="flex items-center gap-2 overflow-hidden">
        <span class="text-xs">📄</span>
        <span class="truncate font-medium text-[11px]" title="${escapeHtml(docTitle)}">${escapeHtml(docTitle)}</span>
      </div>
      <button class="delete-btn text-slate-500 hover:text-rose-400 hover:bg-rose-500/15 p-1 rounded-lg transition-all" title="Delete Document">
        <svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
      </button>
    `;
    item.querySelector('.delete-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      deleteDocument(docTitle);
    });
    corpusList.appendChild(item);
  });
}

async function deleteDocument(title: string) {
  const confirmed = await showConfirmModal('Delete Document', `Are you sure you want to delete "${title}" from active workspace?`);
  if (!confirmed) return;

  try {
    await api.deleteDocument(title, activeWorkspace);
    showToast(`Deleted "${title}"`, 'success');
  } catch (error) {
    showToast(errorMessage(error), 'error');
    return;
  }

  activeCorpusDocs = activeCorpusDocs.filter(d => d !== title);
  renderCorpusList();
  populateCompareDropdowns();
  populateReviewDropdowns();
}

// -------------------------------------------------------------
// CHAT ASSISTANT & FILE UPLOADS
// -------------------------------------------------------------
function initChat() {
  if (!chatForm) return;

  chatForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const text = userInput.value.trim();
    if (!text) return;

    hideMentionDropdown();
    appendUserMessage(text);
    userInput.value = '';

    const { bubbleDiv, messageContent } = appendAssistantMessage('Thinking...');
    const mode = modeSelect ? modeSelect.value : 'hybrid_rerank';

    try {
      const data = await api.chat(text, mode, activeWorkspace, selectedDocumentFilter);
      selectedDocumentFilter = undefined;
      bubbleDiv.innerText = data.answer;
      if (data.retrievedContexts.length > 0) appendContextDrawer(messageContent, data.retrievedContexts);
      if (data.citations.length > 0) appendCitations(messageContent, data.citations);
    } catch (error) {
      bubbleDiv.innerText = errorMessage(error);
    }
  });

  // Suggestion chips listener
  document.addEventListener('click', (e) => {
    const target = e.target as HTMLElement;
    if (target.classList.contains('chip')) {
      const prompt = target.getAttribute('data-prompt');
      if (prompt && userInput) {
        userInput.value = prompt;
        chatForm.dispatchEvent(new Event('submit', { cancelable: true }));
      }
    }
  });

  // Attach button & File Upload
  const headerUploadBtn = document.getElementById('headerUploadBtn');
  if (headerUploadBtn && fileInput) {
    headerUploadBtn.addEventListener('click', () => fileInput.click());
  }

  if (attachBtn && fileInput) {
    attachBtn.addEventListener('click', () => fileInput.click());
  }

  if (fileInput) {
    fileInput.addEventListener('change', async () => {
      const file = fileInput.files?.[0];
      if (file) {
        await processFileUpload(file);
        fileInput.value = '';
      }
    });
  }

  // Drag & drop handlers
  window.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropZoneOverlay?.classList.remove('hidden');
  });

  dropZoneOverlay?.addEventListener('dragleave', (e) => {
    e.preventDefault();
    dropZoneOverlay.classList.add('hidden');
  });

  dropZoneOverlay?.addEventListener('drop', async (e: DragEvent) => {
    e.preventDefault();
    dropZoneOverlay.classList.add('hidden');

    const files = e.dataTransfer?.files;
    if (files && files.length > 0) {
      await processFileUpload(files[0]);
    }
  });

  // Reindex Admin Button
  if (reindexBtn) {
    reindexBtn.addEventListener('click', async () => {
      try {
        const job = await api.reindex(activeWorkspace);
        showToast(`Re-index queued. Job ID: ${job.id}`, 'success');
      } catch (error) {
        showToast(errorMessage(error), error instanceof ApiError && error.status === 409 ? 'warning' : 'error');
      }
    });
  }

  // Autocomplete @ mentions
  if (userInput) {
    userInput.addEventListener('input', () => {
      const val = userInput.value;
      const atIdx = val.lastIndexOf('@');
      if (atIdx !== -1) {
        const query = val.substring(atIdx + 1).toLowerCase();
        const matches = activeCorpusDocs.filter(d => d.toLowerCase().includes(query));
        if (matches.length > 0) {
          renderMentionDropdown(matches, atIdx);
          return;
        }
      }
      hideMentionDropdown();
    });

    userInput.addEventListener('keydown', (e) => {
      if (mentionDropdown && !mentionDropdown.classList.contains('hidden')) {
        const items = mentionDropdown.querySelectorAll('.mention-item');
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          selectedMentionIdx = (selectedMentionIdx + 1) % items.length;
          updateMentionHighlight(items);
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          selectedMentionIdx = (selectedMentionIdx - 1 + items.length) % items.length;
          updateMentionHighlight(items);
        } else if (e.key === 'Enter' || e.key === 'Tab') {
          e.preventDefault();
          (items[selectedMentionIdx] as HTMLElement)?.click();
        } else if (e.key === 'Escape') {
          hideMentionDropdown();
        }
      }
    });
  }
}

async function processFileUpload(file: File) {
  showToast(`Uploading "${file.name}"...`, 'info');
  const { bubbleDiv } = appendAssistantMessage(`📄 Uploading and indexing "${file.name}"...`);

  try {
    const uploaded = await api.upload(file, activeWorkspace);
    if (uploaded.jobId) {
      bubbleDiv.innerText = `⏳ "${uploaded.filename}" was uploaded and is being indexed.`;
      await waitForIngestion(uploaded.jobId, bubbleDiv);
    } else {
      bubbleDiv.innerText = `✅ "${uploaded.filename}" is already indexed.`;
    }
    showToast(`Indexed "${uploaded.filename}"`, 'success');
    await fetchCorpusDocs();
  } catch (error) {
    showToast(errorMessage(error), 'error');
    bubbleDiv.innerText = `❌ ${errorMessage(error)}`;
    return;
  }
}

async function waitForIngestion(jobId: string, bubble: HTMLElement) {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    const job = await api.ingestionJob(jobId);
    bubble.innerText = `⏳ Indexing document: ${job.progressCurrent}/${job.progressTotal || '?'} chunks`;
    if (job.status === 'COMPLETED') {
      bubble.innerText = `✅ Document indexed successfully (${job.progressTotal} chunks).`;
      return;
    }
    if (job.status === 'FAILED' || job.status === 'CANCELLED') throw new Error(job.errorMessage || 'Indexing failed');
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Indexing is still running; check job status later');
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Unexpected application error';
}

function escapeHtml(value: string): string {
  const element = document.createElement('div');
  element.textContent = value;
  return element.innerHTML;
}

function normalizeComparison(response: Record<string, unknown>, docA: string, docB: string) {
  let detail: Record<string, unknown> = {};
  try { detail = JSON.parse(String(response.diffSummary || '{}')); } catch { /* Render the safe defaults below. */ }
  const score = Number(detail.similarityScore ?? 0);
  const differences = Array.isArray(detail.keyDifferences) ? detail.keyDifferences.map(String) : [];
  const conflicts = Array.isArray(detail.conflictPoints) ? detail.conflictPoints.map(String) : [];
  return {
    similarityScore: Math.round(score * 1000) / 10,
    additionsCount: differences.length,
    deletionsCount: 0,
    conflictsCount: conflicts.length,
    docATitle: docA,
    docBTitle: docB,
    diffLines: [
      ...differences.map(text => ({ type: 'mod', text })),
      ...conflicts.map(text => ({ type: 'remove', text }))
    ],
    synthesis: String(detail.overallComparison || response.comparisonResult || 'Comparison completed.')
  };
}

function normalizeReview(response: Record<string, unknown>, scope: string, focus: string) {
  let report: Record<string, unknown> = {};
  try { report = JSON.parse(String(response.reportData || '{}')); } catch { /* Render repository fields below. */ }
  const score = Number(report.qualityScore ?? response.score ?? 0);
  const recommendations = Array.isArray(report.recommendations) ? report.recommendations.map(String) : [];
  return {
    healthScore: score,
    grade: score >= 90 ? 'A' : score >= 80 ? 'B' : score >= 70 ? 'C' : 'D',
    totalIssues: recommendations.length,
    criticalCount: 0,
    highCount: 0,
    mediumCount: recommendations.length,
    lowCount: 0,
    findings: recommendations.map((recommendation, index) => ({
      id: `REC-${index + 1}`,
      title: recommendation,
      severity: 'Medium',
      category: focus === 'all' ? 'Architecture Review' : focus,
      location: scope,
      description: recommendation,
      impact: String(report.feedback || response.feedback || 'Review recommendation'),
      fixCode: 'Review the cited document section and record the resulting architecture decision.'
    }))
  };
}

function mapGraphType(type: string): GraphNode['type'] {
  const normalized = type.toUpperCase();
  if (normalized === 'API') return 'API';
  if (normalized === 'RISK') return 'Risk';
  if (normalized === 'DECISION' || normalized === 'ADR') return 'Decision';
  if (normalized === 'DOCUMENT') return 'Document';
  if (normalized === 'CHUNK') return 'Chunk';
  return 'Entity';
}

function appendUserMessage(text: string): HTMLDivElement {
  const msgDiv = document.createElement('div');
  msgDiv.className = 'flex gap-4 max-w-3xl ml-auto flex-row-reverse';

  const avatarDiv = document.createElement('div');
  avatarDiv.className = 'w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center text-white text-[10px] font-extrabold shrink-0 shadow-md shadow-blue-500/20';
  avatarDiv.innerText = 'YOU';

  const bubbleDiv = document.createElement('div');
  bubbleDiv.className = 'bg-gradient-to-tr from-indigo-950 to-slate-900 border border-indigo-500/40 text-white rounded-2xl rounded-tr-sm p-4 text-sm leading-relaxed shadow-lg shadow-black/40';
  bubbleDiv.innerText = text;

  msgDiv.appendChild(avatarDiv);
  msgDiv.appendChild(bubbleDiv);
  messageList.appendChild(msgDiv);
  messageList.scrollTop = messageList.scrollHeight;

  return bubbleDiv;
}

function appendAssistantMessage(text: string): { bubbleDiv: HTMLDivElement; messageContent: HTMLDivElement } {
  const msgDiv = document.createElement('div');
  msgDiv.className = 'flex gap-4 max-w-3xl';

  const avatarDiv = document.createElement('div');
  avatarDiv.className = 'w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-600 to-cyan-500 flex items-center justify-center text-white text-[10px] font-extrabold shrink-0 shadow-md shadow-indigo-500/20';
  avatarDiv.innerText = 'AI';

  const messageContent = document.createElement('div');
  messageContent.className = 'flex flex-col gap-2 max-w-[85%]';

  const bubbleDiv = document.createElement('div');
  bubbleDiv.className = 'bg-slate-900/80 border border-slate-800 text-slate-100 rounded-2xl rounded-tl-sm p-4 text-sm leading-relaxed shadow-lg shadow-black/40 border-l-4 border-l-indigo-500';
  bubbleDiv.innerText = text;

  messageContent.appendChild(bubbleDiv);
  msgDiv.appendChild(avatarDiv);
  msgDiv.appendChild(messageContent);
  messageList.appendChild(msgDiv);
  messageList.scrollTop = messageList.scrollHeight;

  return { bubbleDiv, messageContent };
}

function appendContextDrawer(container: HTMLDivElement, chunks: DocumentChunk[]) {
  const drawer = document.createElement('div');
  drawer.className = 'bg-slate-900/60 border border-indigo-500/20 rounded-xl overflow-hidden text-xs mt-1';

  const header = document.createElement('div');
  header.className = 'px-3.5 py-2 bg-indigo-500/10 text-indigo-300 font-mono text-[11px] font-medium cursor-pointer flex justify-between items-center hover:bg-indigo-500/15 transition-all';
  header.innerHTML = `<span>🔍 Inspect ${chunks.length} Context Chunks</span> <span>▼</span>`;

  const body = document.createElement('div');
  body.className = 'p-3 flex flex-col gap-2 border-t border-indigo-500/20 hidden';

  chunks.forEach((chunk) => {
    const item = document.createElement('div');
    item.className = 'bg-slate-950 border border-slate-800 p-2.5 rounded-lg text-slate-300 leading-relaxed';
    item.innerHTML = `
      <div class="font-semibold text-cyan-400 mb-1">${escapeHtml(chunk.title || 'Document Chunk')} (Score: ${chunk.score ? chunk.score.toFixed(4) : '1.0'})</div>
      <div>${escapeHtml(chunk.content)}</div>
    `;
    body.appendChild(item);
  });

  header.addEventListener('click', () => {
    const isHidden = body.classList.contains('hidden');
    if (isHidden) {
      body.classList.remove('hidden');
      header.querySelector('span:last-child')!.textContent = '▲';
    } else {
      body.classList.add('hidden');
      header.querySelector('span:last-child')!.textContent = '▼';
    }
  });

  drawer.appendChild(header);
  drawer.appendChild(body);
  container.appendChild(drawer);
}

function appendCitations(container: HTMLDivElement, citations: Array<{ citationId: string; documentTitle: string; sectionTitle?: string; pageNumber?: number; score: number }>) {
  const list = document.createElement('div');
  list.className = 'flex flex-wrap gap-2 mt-2';
  citations.forEach(citation => {
    const item = document.createElement('span');
    item.className = 'text-[10px] font-mono rounded-lg border border-slate-700 bg-slate-900 px-2 py-1 text-slate-300';
    const location = citation.pageNumber ? ` p.${citation.pageNumber}` : citation.sectionTitle ? ` · ${citation.sectionTitle}` : '';
    item.textContent = `[${citation.citationId}] ${citation.documentTitle}${location}`;
    item.title = `Retrieval score ${citation.score.toFixed(4)}`;
    list.appendChild(item);
  });
  container.appendChild(list);
}

function renderMentionDropdown(matches: string[], atIdx: number) {
  if (!mentionDropdown) return;
  mentionDropdown.innerHTML = '';
  selectedMentionIdx = 0;

  matches.forEach((docTitle, idx) => {
    const item = document.createElement('div');
    item.className = `mention-item flex items-center gap-2 p-2 px-3 rounded-xl text-xs text-slate-300 hover:bg-indigo-500/20 hover:text-white cursor-pointer transition-all ${idx === 0 ? 'bg-indigo-500/20 text-white font-semibold' : ''}`;
    const icon = document.createElement('span');
    icon.textContent = '📄';
    const label = document.createElement('span');
    label.textContent = docTitle;
    item.append(icon, label);
    item.addEventListener('click', () => {
      const beforeAt = userInput.value.substring(0, atIdx);
      selectedDocumentFilter = docTitle;
      userInput.value = `${beforeAt}@${docTitle} `;
      userInput.focus();
      hideMentionDropdown();
    });
    mentionDropdown.appendChild(item);
  });

  mentionDropdown.classList.remove('hidden');
  mentionDropdown.classList.add('flex');
}

function updateMentionHighlight(items: NodeListOf<Element>) {
  items.forEach((item, idx) => {
    if (idx === selectedMentionIdx) {
      item.classList.add('bg-indigo-500/20', 'text-white', 'font-semibold');
    } else {
      item.classList.remove('bg-indigo-500/20', 'text-white', 'font-semibold');
    }
  });
}

function hideMentionDropdown() {
  if (!mentionDropdown) return;
  mentionDropdown.classList.add('hidden');
  mentionDropdown.classList.remove('flex');
}

// -------------------------------------------------------------
// DOCUMENT INTELLIGENCE ARTIFACTS GRID VIEW
// -------------------------------------------------------------
function initIntelligenceView() {
  if (artifactCatBtns) {
    artifactCatBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        artifactCatBtns.forEach(b => b.className = 'artifact-cat-btn px-3.5 py-1.5 rounded-lg text-xs font-semibold text-slate-400 hover:bg-slate-800 hover:text-slate-200');
        btn.className = 'artifact-cat-btn px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600/20 text-indigo-300 border border-indigo-500/30';
        currentArtifactCatFilter = btn.getAttribute('data-cat') || 'all';
        renderArtifactsGrid();
      });
    });
  }

  if (artifactSearchInput) {
    artifactSearchInput.addEventListener('input', () => {
      renderArtifactsGrid();
    });
  }

  if (extractArtifactsBtn) {
    extractArtifactsBtn.addEventListener('click', async () => {
      if (activeCorpusDocs.length === 0) {
        showToast('Upload a document before extracting artifacts', 'warning');
        return;
      }
      try {
        await api.extractArtifact(activeCorpusDocs[0], 'requirements', activeWorkspace);
        await loadArtifacts();
        showToast('Artifact extraction completed', 'success');
      } catch (error) {
        showToast(errorMessage(error), 'error');
      }
    });
  }

  if (artifactModalCloseBtn && artifactDetailModal) {
    artifactModalCloseBtn.addEventListener('click', () => {
      artifactDetailModal.classList.add('hidden');
    });
  }
}

function renderArtifactsGrid() {
  if (!artifactsGrid) return;
  artifactsGrid.innerHTML = '';

  const search = artifactSearchInput ? artifactSearchInput.value.toLowerCase().trim() : '';

  const filtered = intelligenceArtifacts.filter(art => {
    const matchesCat = currentArtifactCatFilter === 'all' || art.category === currentArtifactCatFilter;
    const matchesSearch = !search || art.title.toLowerCase().includes(search) || art.summary.toLowerCase().includes(search) || art.tags.some(t => t.toLowerCase().includes(search));
    return matchesCat && matchesSearch;
  });

  if (filtered.length === 0) {
    artifactsGrid.innerHTML = '<div class="col-span-4 text-center py-12 text-slate-500 text-xs">No intelligence artifacts found for this filter.</div>';
    return;
  }

  filtered.forEach(art => {
    const card = document.createElement('div');
    
    const catBadgeColor = art.category === 'requirements' ? 'bg-blue-500/15 text-blue-400 border-blue-500/30' :
                         (art.category === 'apis' ? 'bg-purple-500/15 text-purple-400 border-purple-500/30' :
                         (art.category === 'risks' ? 'bg-rose-500/15 text-rose-400 border-rose-500/30' : 'bg-amber-500/15 text-amber-400 border-amber-500/30'));

    const icon = art.category === 'requirements' ? '📋' :
                (art.category === 'apis' ? '🔌' :
                (art.category === 'risks' ? '⚠️' : '💡'));

    const sevBadgeColor = art.severityOrPriority === 'Critical' ? 'bg-rose-500/20 text-rose-300' :
                         (art.severityOrPriority === 'High' ? 'bg-amber-500/20 text-amber-300' : 'bg-slate-800 text-slate-400');

    card.className = 'bg-slate-900/80 border border-slate-800 hover:border-indigo-500/50 rounded-2xl p-5 shadow-xl hover:shadow-2xl hover:shadow-indigo-500/10 transition-all flex flex-col justify-between cursor-pointer group';
    card.innerHTML = `
      <div class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="text-[10px] font-mono font-bold uppercase tracking-wider px-2 py-0.5 rounded border ${catBadgeColor} flex items-center gap-1">
            <span>${icon}</span> <span>${escapeHtml(art.category)}</span>
          </span>
          <span class="text-[10px] font-mono px-2 py-0.5 rounded font-semibold ${sevBadgeColor}">
            ${escapeHtml(art.severityOrPriority)}
          </span>
        </div>
        <h4 class="text-sm font-bold text-white group-hover:text-indigo-300 transition-colors leading-snug">${escapeHtml(art.title)}</h4>
        <p class="text-xs text-slate-400 leading-relaxed line-clamp-3">${escapeHtml(art.summary)}</p>
      </div>

      <div class="mt-4 pt-3 border-t border-slate-800/80 flex flex-col gap-2">
        <div class="flex flex-wrap gap-1">
          ${art.tags.map(t => `<span class="text-[9px] font-mono bg-slate-950 text-slate-400 border border-slate-800 px-1.5 py-0.5 rounded">${escapeHtml(t)}</span>`).join('')}
        </div>
        <div class="flex items-center justify-between text-[10px] text-slate-500 mt-1">
          <span class="truncate max-w-[150px]">📄 ${escapeHtml(art.docSource)}</span>
          <span class="text-indigo-400 group-hover:translate-x-0.5 transition-transform font-bold">Details &rarr;</span>
        </div>
      </div>
    `;

    card.addEventListener('click', () => openArtifactModal(art));
    artifactsGrid.appendChild(card);
  });
}

async function loadArtifacts() {
  if (!activeWorkspace) return;
  try {
    const artifacts = await api.artifacts(activeWorkspace);
    intelligenceArtifacts = artifacts.map(toArtifact);
    if (dashboardArtifactCount) dashboardArtifactCount.innerText = `${intelligenceArtifacts.length} ${intelligenceArtifacts.length === 1 ? 'Spec' : 'Specs'}`;
  } catch (error) {
    intelligenceArtifacts = [];
    if (dashboardArtifactCount) dashboardArtifactCount.innerText = '0 Specs';
    showToast(errorMessage(error), 'error');
  }
  renderArtifactsGrid();
}

function toArtifact(value: KnowledgeArtifactDto): Artifact {
  let structured: Record<string, unknown> = {};
  try { structured = JSON.parse(value.structuredData || '{}'); } catch { /* Keep raw content below. */ }
  const category = normalizeArtifactCategory(value.artifactType);
  const keyPoints = Array.isArray(structured.keyPoints) ? structured.keyPoints.map(String) : [];
  return {
    id: value.id,
    category,
    title: value.title,
    summary: typeof structured.summary === 'string' ? structured.summary : value.content.slice(0, 240),
    tags: keyPoints.slice(0, 4),
    docSource: value.title,
    detail: value.structuredData || value.content,
    severityOrPriority: 'Medium'
  };
}

function normalizeArtifactCategory(type: string): Artifact['category'] {
  const normalized = type.toLowerCase();
  if (normalized.includes('api')) return 'apis';
  if (normalized.includes('risk')) return 'risks';
  if (normalized.includes('decision') || normalized.includes('adr')) return 'decisions';
  return 'requirements';
}

function openArtifactModal(art: Artifact) {
  if (!artifactDetailModal) return;
  const icon = art.category === 'requirements' ? '📋' :
              (art.category === 'apis' ? '🔌' :
              (art.category === 'risks' ? '⚠️' : '💡'));

  if (artifactModalIcon) artifactModalIcon.innerText = icon;
  if (artifactModalCategory) artifactModalCategory.innerText = art.category.toUpperCase();
  if (artifactModalTitle) artifactModalTitle.innerText = art.title;
  
  if (artifactModalBody) {
    artifactModalBody.innerHTML = `
      <div class="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
        <div class="text-[11px] font-bold text-slate-400 uppercase tracking-wider">SUMMARY & OVERVIEW</div>
        <div class="text-slate-200">${escapeHtml(art.summary)}</div>
      </div>
      
      <div class="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
        <div class="text-[11px] font-bold text-indigo-400 uppercase tracking-wider">FULL TECHNICAL SPECIFICATION</div>
        <pre class="font-mono text-[11px] text-slate-300 whitespace-pre-wrap leading-relaxed">${escapeHtml(art.detail)}</pre>
      </div>

      <div class="flex items-center justify-between text-[11px] text-slate-400 pt-2">
        <span>Source Document: <strong class="text-white">${escapeHtml(art.docSource)}</strong></span>
        <span>Priority Level: <strong class="text-indigo-300">${escapeHtml(art.severityOrPriority)}</strong></span>
      </div>
    `;
  }

  artifactDetailModal.classList.remove('hidden');
}

// -------------------------------------------------------------
// MULTI-DOCUMENT COMPARISON DIFF STUDIO VIEW
// -------------------------------------------------------------
function initCompareView() {
  populateCompareDropdowns();

  if (runCompareBtn) {
    runCompareBtn.addEventListener('click', async () => {
      const docA = compareDocA ? compareDocA.value : '';
      const docB = compareDocB ? compareDocB.value : '';
      const mode = compareMode ? compareMode.value : 'semantic';

      if (!docA || !docB) {
        showToast('Please select both Document A and Document B to compare', 'warning');
        return;
      }

      if (docA === docB) {
        showToast('Please select two distinct documents for comparison', 'warning');
        return;
      }

      showToast(`Comparing "${docA}" vs "${docB}"...`, 'info');
      runCompareBtn.disabled = true;
      runCompareBtn.innerHTML = '<span>⚡</span> Comparing...';

      try {
        const response = await api.compare(docA, docB, activeWorkspace) as Record<string, unknown>;
        renderCompareResults(normalizeComparison(response, docA, docB), docA, docB);
      } catch (error) {
        showToast(errorMessage(error), 'error');
        compareResults.innerHTML = `<div class="p-6 text-sm text-rose-300">${escapeHtml(errorMessage(error))}</div>`;
      } finally {
        runCompareBtn.disabled = false;
        runCompareBtn.innerHTML = '<span>⚔️</span> Compare Documents';
      }
    });
  }
}

function populateCompareDropdowns() {
  if (!compareDocA || !compareDocB) return;
  const valA = compareDocA.value;
  const valB = compareDocB.value;

  compareDocA.innerHTML = '<option value="">Select Document A (Base)...</option>';
  compareDocB.innerHTML = '<option value="">Select Document B (Target)...</option>';

  activeCorpusDocs.forEach((doc, idx) => {
    const optA = document.createElement('option');
    optA.value = doc;
    optA.innerText = `📄 ${doc}`;
    if (doc === valA || (!valA && idx === 0)) optA.selected = true;
    compareDocA.appendChild(optA);

    const optB = document.createElement('option');
    optB.value = doc;
    optB.innerText = `📄 ${doc}`;
    if (doc === valB || (!valB && idx === 1)) optB.selected = true;
    compareDocB.appendChild(optB);
  });
}

function renderCompareResults(data: any, docA: string, docB: string) {
  if (!compareResults) return;
  compareResults.innerHTML = `
    <!-- Top Metric Summary Cards -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col gap-1">
        <span class="text-[10px] font-bold text-slate-400 uppercase">SEMANTIC SIMILARITY</span>
        <div class="flex items-baseline gap-2">
          <span class="text-2xl font-extrabold text-cyan-400">${data.similarityScore ?? 0}%</span>
          <span class="text-xs text-slate-400">match</span>
        </div>
      </div>
      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col gap-1">
        <span class="text-[10px] font-bold text-slate-400 uppercase">ADDED CLAUSES (+)</span>
        <span class="text-2xl font-extrabold text-emerald-400">+${data.additionsCount ?? 0}</span>
      </div>
      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col gap-1">
        <span class="text-[10px] font-bold text-slate-400 uppercase">REMOVED CLAUSES (-)</span>
        <span class="text-2xl font-extrabold text-rose-400">-${data.deletionsCount ?? 0}</span>
      </div>
      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col gap-1">
        <span class="text-[10px] font-bold text-slate-400 uppercase">CONFLICTING CLAUSES</span>
        <span class="text-2xl font-extrabold text-amber-400">${data.conflictsCount ?? 0} Conflict</span>
      </div>
    </div>

    <!-- Side-by-Side Diff Panels -->
    <div class="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 flex flex-col gap-4 shadow-xl">
      <div class="flex items-center justify-between border-b border-slate-800 pb-3">
        <div class="flex items-center gap-2">
          <span class="text-xs font-bold text-slate-300">📄 ${escapeHtml(docA)}</span>
          <span class="text-xs text-slate-500">vs</span>
          <span class="text-xs font-bold text-slate-300">📄 ${escapeHtml(docB)}</span>
        </div>
        <span class="text-[10px] font-mono bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2.5 py-0.5 rounded">INLINE DIFF VIEW</span>
      </div>

      <div class="flex flex-col gap-1 max-h-[350px] overflow-y-auto pr-2">
        ${data.diffLines ? data.diffLines.map((line: any) => {
          const lineClass = line.type === 'add' ? 'diff-line-add' :
                           (line.type === 'remove' ? 'diff-line-remove' :
                           (line.type === 'mod' ? 'diff-line-mod' : 'diff-line-same'));
          const prefix = line.type === 'add' ? '+ ' : (line.type === 'remove' ? '- ' : (line.type === 'mod' ? '~ ' : '  '));
          return `<div class="${lineClass}">${prefix}${escapeHtml(String(line.text ?? ''))}</div>`;
        }).join('') : ''}
      </div>
    </div>

    <!-- AI Synthesis Box -->
    <div class="bg-gradient-to-r from-indigo-950/80 to-slate-900 border border-indigo-500/40 p-5 rounded-2xl shadow-xl flex flex-col gap-2">
      <div class="flex items-center gap-2 text-indigo-300 font-bold text-xs">
        <span>⚡</span> AI Conflict Resolution & Synthesis
      </div>
      <div class="text-xs text-slate-200 leading-relaxed font-mono whitespace-pre-wrap">${escapeHtml(String(data.synthesis ?? ''))}</div>
    </div>
  `;
}

// -------------------------------------------------------------
// AI SENIOR REVIEWER FINDINGS REPORT VIEW
// -------------------------------------------------------------
function initReviewView() {
  populateReviewDropdowns();

  if (runReviewBtn) {
    runReviewBtn.addEventListener('click', async () => {
      const scope = reviewDocSelect ? reviewDocSelect.value : 'all';
      const focus = reviewFocusSelect ? reviewFocusSelect.value : 'all';

      showToast(`Running AI Senior Review for scope "${scope}"...`, 'info');
      runReviewBtn.disabled = true;
      runReviewBtn.innerHTML = '<span>⚡</span> Analyzing...';

      try {
        const documentTitle = scope === 'all' ? activeCorpusDocs[0] : scope;
        if (!documentTitle) throw new Error('Upload a document before running a review');
        const response = await api.review(documentTitle, activeWorkspace) as Record<string, unknown>;
        renderReviewDashboard(normalizeReview(response, scope, focus));
      } catch (error) {
        showToast(errorMessage(error), 'error');
        reviewResults.innerHTML = `<div class="p-6 text-sm text-rose-300">${escapeHtml(errorMessage(error))}</div>`;
      } finally {
        runReviewBtn.disabled = false;
        runReviewBtn.innerHTML = '<span>🔍</span> Run AI Review';
      }
    });
  }
}

function populateReviewDropdowns() {
  if (!reviewDocSelect) return;
  const currentVal = reviewDocSelect.value;
  reviewDocSelect.innerHTML = '<option value="all">🌐 Entire Active Workspace</option>';

  activeCorpusDocs.forEach(doc => {
    const opt = document.createElement('option');
    opt.value = doc;
    opt.innerText = `📄 ${doc}`;
    if (doc === currentVal) opt.selected = true;
    reviewDocSelect.appendChild(opt);
  });
}

function renderReviewDashboard(data: any) {
  if (!reviewResults) return;

  reviewResults.innerHTML = `
    <!-- Top Health Gauge Bar -->
    <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
      <div class="bg-gradient-to-tr from-slate-900 to-slate-950 border border-slate-800 p-5 rounded-2xl flex flex-col justify-between md:col-span-2">
        <span class="text-[10px] font-bold text-slate-400 uppercase tracking-wider">WORKSPACE COMPLIANCE HEALTH</span>
        <div class="flex items-baseline gap-3 my-2">
          <span class="text-4xl font-extrabold text-emerald-400">${data.healthScore ?? 0}</span>
          <span class="text-sm font-bold text-emerald-300 font-mono bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">GRADE ${escapeHtml(String(data.grade ?? 'N/A'))}</span>
        </div>
        <span class="text-[11px] text-slate-400">Automated result; validate findings manually before approval.</span>
      </div>

      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col justify-center gap-1">
        <span class="text-[10px] font-bold text-rose-400 uppercase">🔴 CRITICAL</span>
        <span class="text-3xl font-extrabold text-rose-400">${data.criticalCount ?? 0}</span>
      </div>

      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col justify-center gap-1">
        <span class="text-[10px] font-bold text-amber-400 uppercase">🟠 HIGH</span>
        <span class="text-3xl font-extrabold text-amber-400">${data.highCount ?? 0}</span>
      </div>

      <div class="bg-slate-900/80 border border-slate-800 p-4 rounded-2xl flex flex-col justify-center gap-1">
        <span class="text-[10px] font-bold text-cyan-400 uppercase">🔵 MEDIUM / LOW</span>
        <span class="text-3xl font-extrabold text-cyan-400">${(data.mediumCount ?? 0) + (data.lowCount ?? 0)}</span>
      </div>
    </div>

    <!-- Findings Card List -->
    <div class="flex flex-col gap-4">
      <h4 class="text-sm font-bold text-white flex items-center justify-between">
        <span>Detailed Review Findings (${data.findings?.length ?? 0})</span>
        <span class="text-[10px] font-mono text-slate-500">Sorted by Severity</span>
      </h4>

      ${data.findings ? data.findings.map((f: any) => {
        const sevColor = f.severity === 'Critical' ? 'bg-rose-500/20 text-rose-300 border-rose-500/30' :
                        (f.severity === 'High' ? 'bg-amber-500/20 text-amber-300 border-amber-500/30' :
                        'bg-cyan-500/20 text-cyan-300 border-cyan-500/30');

        return `
          <div class="bg-slate-900/90 border border-slate-800 rounded-2xl p-5 flex flex-col gap-3 shadow-lg hover:border-slate-750 transition-all">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <span class="text-[10px] font-mono font-bold px-2 py-0.5 rounded border ${sevColor}">${escapeHtml(String(f.severity ?? ''))}</span>
                <span class="text-xs font-mono text-indigo-400 font-semibold">${escapeHtml(String(f.id ?? ''))}</span>
                <span class="text-xs text-slate-400">| ${escapeHtml(String(f.category ?? ''))}</span>
              </div>
              <span class="text-[11px] font-mono text-slate-500">📍 ${escapeHtml(String(f.location ?? ''))}</span>
            </div>

            <h5 class="text-sm font-bold text-white">${escapeHtml(String(f.title ?? ''))}</h5>
            <p class="text-xs text-slate-300 leading-relaxed">${escapeHtml(String(f.description ?? ''))}</p>
            <div class="text-xs text-amber-300/90 bg-amber-500/10 p-2.5 rounded-xl border border-amber-500/20">
              ⚡ <strong>Impact:</strong> ${escapeHtml(String(f.impact ?? ''))}
            </div>

            ${f.fixCode ? `
              <div class="bg-slate-950 border border-slate-800 rounded-xl p-3 space-y-2 mt-1">
                <div class="flex items-center justify-between text-[10px] font-mono text-slate-400 border-b border-slate-800 pb-1.5">
                  <span>SUGGESTED REFACTOR FIX</span>
                  <button class="copy-fix-btn hover:text-white font-bold text-indigo-400" data-code="${encodeURIComponent(f.fixCode)}">Copy Fix</button>
                </div>
                <pre class="font-mono text-[11px] text-emerald-300 whitespace-pre-wrap overflow-x-auto leading-relaxed">${escapeHtml(String(f.fixCode))}</pre>
              </div>
            ` : ''}
          </div>
        `;
      }).join('') : ''}
    </div>
  `;

  // Attach copy listeners
  reviewResults.querySelectorAll('.copy-fix-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const code = decodeURIComponent((e.target as HTMLElement).getAttribute('data-code') || '');
      navigator.clipboard.writeText(code);
      showToast('Copied fix code to clipboard!', 'success');
    });
  });
}

// -------------------------------------------------------------
// D3.js FORCE-DIRECTED KNOWLEDGE GRAPH VIEW
// -------------------------------------------------------------
let graphSimulation: d3.Simulation<GraphNode, GraphLink> | null = null;
let currentGraphFilter = 'all';

function initGraphView() {
  if (refreshGraphBtn) {
    refreshGraphBtn.addEventListener('click', () => {
      showToast('Refreshing Knowledge Graph...', 'info');
      renderKnowledgeGraph();
    });
  }

  if (graphFilterBtns) {
    graphFilterBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        graphFilterBtns.forEach(b => b.className = 'graph-filter-btn px-2.5 py-1 rounded-lg text-xs font-semibold text-slate-400 bg-slate-800');
        btn.className = 'graph-filter-btn px-2.5 py-1 rounded-lg text-xs font-semibold bg-indigo-600 text-white';
        currentGraphFilter = btn.getAttribute('data-graph-filter') || 'all';
        renderKnowledgeGraph();
      });
    });
  }

  if (graphNodeSearch) {
    graphNodeSearch.addEventListener('input', () => {
      const term = graphNodeSearch.value.toLowerCase().trim();
      d3.selectAll('.d3-node').attr('opacity', (d: any) => {
        if (!term) return 1;
        return d.label.toLowerCase().includes(term) || d.type.toLowerCase().includes(term) ? 1 : 0.15;
      });
    });
  }

  if (closeDrawerBtn && nodeDetailDrawer) {
    closeDrawerBtn.addEventListener('click', () => {
      nodeDetailDrawer.classList.add('hidden');
    });
  }
}

async function renderKnowledgeGraph() {
  const svgElem = document.getElementById('knowledge-graph-svg') as unknown as SVGSVGElement;
  if (!svgElem) return;

  const svg = d3.select(svgElem);
  svg.selectAll('*').remove();

  const container = document.getElementById('d3-graph-container');
  const width = container ? container.clientWidth : 800;
  const height = container ? container.clientHeight : 600;

  try {
    const raw = await api.graph(activeWorkspace);
    const graphData: { nodes: GraphNode[]; links: GraphLink[] } = {
      nodes: raw.entities.map(entity => ({
        id: entity.id,
        label: entity.name,
        type: mapGraphType(entity.entityType),
        properties: entity.description ? { description: entity.description } : undefined
      })),
      links: raw.relationships.map(relationship => ({
        source: relationship.sourceEntityId,
        target: relationship.targetEntityId,
        relationship: relationship.relationshipType
      }))
    };

    // Filter nodes if applicable
    let filteredNodes = graphData.nodes;
    if (currentGraphFilter !== 'all') {
      filteredNodes = graphData.nodes.filter(n => n.type === currentGraphFilter);
    }
    const nodeIds = new Set(filteredNodes.map(n => n.id));
    const filteredLinks = graphData.links.filter(l => {
      const srcId = typeof l.source === 'object' ? (l.source as any).id : l.source;
      const tgtId = typeof l.target === 'object' ? (l.target as any).id : l.target;
      return nodeIds.has(srcId) && nodeIds.has(tgtId);
    });

    // Create main g container for Zoom/Pan
    const g = svg.append('g');

    const zoomBehavior = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.3, 4])
      .on('zoom', (event) => {
        g.attr('transform', event.transform);
      });

    svg.call(zoomBehavior as any);

    if (graphZoomInBtn) graphZoomInBtn.onclick = () => svg.transition().call(zoomBehavior.scaleBy as any, 1.3);
    if (graphZoomOutBtn) graphZoomOutBtn.onclick = () => svg.transition().call(zoomBehavior.scaleBy as any, 0.7);
    if (graphResetBtn) graphResetBtn.onclick = () => svg.transition().call(zoomBehavior.transform as any, d3.zoomIdentity);

    // Color mapper by node type
    const getNodeColor = (type: string) => {
      switch (type) {
        case 'Document': return '#38bdf8'; // Cyan
        case 'Chunk': return '#818cf8'; // Indigo
        case 'Entity': return '#34d399'; // Emerald
        case 'API': return '#c084fc'; // Purple
        case 'Risk': return '#fb7185'; // Rose
        case 'Decision': return '#fbbf24'; // Amber
        default: return '#94a3b8';
      }
    };

    // Force Simulation Setup
    graphSimulation = d3.forceSimulation<GraphNode>(filteredNodes)
      .force('link', d3.forceLink<GraphNode, GraphLink>(filteredLinks).id((d: any) => d.id).distance(100))
      .force('charge', d3.forceManyBody().strength(-300))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collision', d3.forceCollide().radius(35));

    // Render Links
    const link = g.append('g')
      .attr('class', 'links')
      .selectAll('line')
      .data(filteredLinks)
      .enter()
      .append('line')
      .attr('class', 'd3-link')
      .attr('stroke', '#475569')
      .attr('stroke-width', 1.5);

    // Render Link Labels
    const linkText = g.append('g')
      .selectAll('text')
      .data(filteredLinks)
      .enter()
      .append('text')
      .text(d => d.relationship || 'relates_to')
      .attr('font-size', '9px')
      .attr('fill', '#64748b')
      .attr('font-family', 'monospace')
      .attr('text-anchor', 'middle');

    // Render Node Groups
    const node = g.append('g')
      .attr('class', 'nodes')
      .selectAll('g')
      .data(filteredNodes)
      .enter()
      .append('g')
      .attr('class', 'd3-node')
      .call(d3.drag<SVGGElement, GraphNode>()
        .on('start', (event, d) => {
          if (!event.active && graphSimulation) graphSimulation.alphaTarget(0.3).restart();
          d.fx = d.x;
          d.fy = d.y;
        })
        .on('drag', (event, d) => {
          d.fx = event.x;
          d.fy = event.y;
        })
        .on('end', (event, d) => {
          if (!event.active && graphSimulation) graphSimulation.alphaTarget(0);
          d.fx = null;
          d.fy = null;
        }) as any
      );

    // Node Circle
    node.append('circle')
      .attr('r', d => d.type === 'Document' ? 18 : 12)
      .attr('fill', d => getNodeColor(d.type))
      .attr('stroke', '#0f172a')
      .attr('stroke-width', 2);

    // Node Icon / Text inside
    node.append('text')
      .text(d => d.type === 'Document' ? '📄' : (d.type === 'API' ? '🔌' : (d.type === 'Risk' ? '⚠️' : '💡')))
      .attr('text-anchor', 'middle')
      .attr('dy', '0.35em')
      .attr('font-size', '10px');

    // Node Label under circle
    node.append('text')
      .text(d => d.label)
      .attr('x', 0)
      .attr('y', 26)
      .attr('text-anchor', 'middle')
      .attr('fill', '#e2e8f0')
      .attr('font-size', '10px')
      .attr('font-weight', '600')
      .attr('font-family', 'sans-serif');

    // Click event for Node Detail Drawer
    node.on('click', (_, d) => {
      openNodeDrawer(d, filteredLinks);
    });

    // Simulation Ticks
    graphSimulation.on('tick', () => {
      link
        .attr('x1', (d: any) => d.source.x)
        .attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x)
        .attr('y2', (d: any) => d.target.y);

      linkText
        .attr('x', (d: any) => (d.source.x + d.target.x) / 2)
        .attr('y', (d: any) => (d.source.y + d.target.y) / 2 - 4);

      node
        .attr('transform', (d: any) => `translate(${d.x},${d.y})`);
    });

  } catch (err) {
    console.error('Error rendering D3 graph:', err);
  }
}

function generateSampleGraphData(): { nodes: GraphNode[]; links: GraphLink[] } {
  const nodes: GraphNode[] = [
    { id: 'doc-1', label: 'groundwork-architecture-v2.pdf', type: 'Document' },
    { id: 'doc-2', label: 'api-spec-gateway.json', type: 'Document' },
    { id: 'doc-3', label: 'compliance-security-matrix.md', type: 'Document' },
    { id: 'chunk-1', label: 'pgvector Indexing Chunk #12', type: 'Chunk', docSource: 'groundwork-architecture-v2.pdf' },
    { id: 'chunk-2', label: 'RRF Reranking Chunk #04', type: 'Chunk', docSource: 'groundwork-architecture-v2.pdf' },
    { id: 'entity-1', label: 'Reciprocal Rank Fusion', type: 'Entity' },
    { id: 'entity-2', label: 'PostgreSQL pgvector', type: 'Entity' },
    { id: 'api-1', label: 'POST /api/chat', type: 'API' },
    { id: 'api-2', label: 'POST /api/compare', type: 'API' },
    { id: 'risk-1', label: 'Memory Spike on Ingestion', type: 'Risk' },
    { id: 'adr-1', label: 'ADR-001 RRF vs Cross-Encoder', type: 'Decision' }
  ];

  const links: GraphLink[] = [
    { source: 'doc-1', target: 'chunk-1', relationship: 'contains_chunk' },
    { source: 'doc-1', target: 'chunk-2', relationship: 'contains_chunk' },
    { source: 'chunk-1', target: 'entity-2', relationship: 'references' },
    { source: 'chunk-2', target: 'entity-1', relationship: 'references' },
    { source: 'doc-2', target: 'api-1', relationship: 'defines_endpoint' },
    { source: 'doc-2', target: 'api-2', relationship: 'defines_endpoint' },
    { source: 'doc-1', target: 'risk-1', relationship: 'identifies_risk' },
    { source: 'doc-1', target: 'adr-1', relationship: 'records_decision' },
    { source: 'api-1', target: 'entity-1', relationship: 'uses_algorithm' }
  ];

  return { nodes, links };
}

function openNodeDrawer(node: GraphNode, allLinks: GraphLink[]) {
  if (!nodeDetailDrawer) return;

  if (drawerNodeBadge) {
    drawerNodeBadge.innerText = node.type.toUpperCase();
  }
  if (drawerNodeTitle) {
    drawerNodeTitle.innerText = node.label;
  }

  const connected = allLinks.filter(l => {
    const srcId = typeof l.source === 'object' ? (l.source as any).id : l.source;
    const tgtId = typeof l.target === 'object' ? (l.target as any).id : l.target;
    return srcId === node.id || tgtId === node.id;
  });

  if (drawerNodeBody) {
    drawerNodeBody.innerHTML = `
      <div class="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-1">
        <div class="text-[10px] font-bold text-slate-400 uppercase">NODE METADATA</div>
        <div class="text-xs text-slate-200">ID: <span class="font-mono text-indigo-300">${escapeHtml(node.id)}</span></div>
        <div class="text-xs text-slate-200">Category: <span class="font-bold text-cyan-300">${escapeHtml(node.type)}</span></div>
        ${node.docSource ? `<div class="text-xs text-slate-400">Source: ${escapeHtml(node.docSource)}</div>` : ''}
      </div>

      <div class="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
        <div class="text-[10px] font-bold text-indigo-400 uppercase">CONNECTED RELATIONSHIPS (${connected.length})</div>
        <div class="space-y-1.5">
          ${connected.map(l => {
            const srcLabel = typeof l.source === 'object' ? (l.source as any).label : l.source;
            const tgtLabel = typeof l.target === 'object' ? (l.target as any).label : l.target;
            return `
              <div class="text-[11px] bg-slate-900 p-2 rounded-lg border border-slate-800 text-slate-300 flex items-center justify-between">
                <span>${escapeHtml(String(srcLabel))}</span>
                <span class="font-mono text-indigo-400 text-[9px] px-1 bg-indigo-500/10 rounded">${escapeHtml(l.relationship)}</span>
                <span>${escapeHtml(String(tgtLabel))}</span>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    `;
  }

  nodeDetailDrawer.classList.remove('hidden');
}

// -------------------------------------------------------------
// LIGHT / DARK MODE THEME SWITCHER ENGINE
// -------------------------------------------------------------
function initTheme() {
  const themeToggleBtn = document.getElementById('themeToggleBtn');
  const themeToggleIcon = document.getElementById('themeToggleIcon');
  const themeToggleText = document.getElementById('themeToggleText');

  const savedTheme = localStorage.getItem('groundwork_theme') || 
    (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');

  applyTheme(savedTheme);

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      const isLight = document.documentElement.classList.contains('light');
      const nextTheme = isLight ? 'dark' : 'light';
      applyTheme(nextTheme);
      showToast(`Switched to ${nextTheme} mode`, 'info');
    });
  }

  function applyTheme(theme: string) {
    if (theme === 'light') {
      document.documentElement.classList.add('light');
      document.documentElement.classList.remove('dark');
      if (themeToggleIcon) themeToggleIcon.innerText = '☀️';
      if (themeToggleText) themeToggleText.innerText = 'Light';
    } else {
      document.documentElement.classList.remove('light');
      document.documentElement.classList.add('dark');
      if (themeToggleIcon) themeToggleIcon.innerText = '🌙';
      if (themeToggleText) themeToggleText.innerText = 'Dark';
    }
    localStorage.setItem('groundwork_theme', theme);
  }
}

// -------------------------------------------------------------
// APPLICATION INITIALIZATION
// -------------------------------------------------------------
document.addEventListener('DOMContentLoaded', async () => {
  initTheme();
  initAuth();
  initTabs();
  await initWorkspace();
  initChat();
  initIntelligenceView();
  initCompareView();
  initReviewView();
  initGraphView();
  await fetchCorpusDocs();
});
