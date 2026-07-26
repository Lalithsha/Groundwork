const messageList = document.getElementById('messageList') as HTMLDivElement;
const chatForm = document.getElementById('chatForm') as HTMLFormElement;
const userInput = document.getElementById('userInput') as HTMLInputElement;
const modeSelect = document.getElementById('retrievalModeSelect') as HTMLSelectElement;
const reindexBtn = document.getElementById('reindexBtn') as HTMLButtonElement;
const attachBtn = document.getElementById('attachBtn') as HTMLButtonElement;
const fileInput = document.getElementById('fileInput') as HTMLInputElement;
const dropZoneOverlay = document.getElementById('dropZoneOverlay') as HTMLDivElement;
const sidebarDropzone = document.getElementById('sidebarDropzone') as HTMLDivElement;
const corpusList = document.getElementById('corpusList') as HTMLDivElement;
const mentionDropdown = document.getElementById('mentionDropdown') as HTMLDivElement;
const toastContainer = document.getElementById('toastContainer') as HTMLDivElement;
const confirmModal = document.getElementById('confirmModal') as HTMLDivElement;
const modalTitle = document.getElementById('modalTitle') as HTMLHeadingElement;
const modalMessage = document.getElementById('modalMessage') as HTMLDivElement;
const modalCancelBtn = document.getElementById('modalCancelBtn') as HTMLButtonElement;
const modalConfirmBtn = document.getElementById('modalConfirmBtn') as HTMLButtonElement;

interface DocumentChunk {
  id: string;
  title: string;
  content: string;
  sourceType: string;
  score: number;
}

let activeCorpusDocs: string[] = [];
let selectedMentionIdx = 0;

// Custom Toast Component Helper
function showToast(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info') {
  if (!toastContainer) return;
  const toast = document.createElement('div');
  
  const borderClass = type === 'success' ? 'border-l-4 border-l-emerald-500 border-slate-800' :
                     (type === 'error' ? 'border-l-4 border-l-rose-500 border-slate-800' :
                     (type === 'warning' ? 'border-l-4 border-l-amber-500 border-slate-800' : 'border-l-4 border-l-indigo-500 border-slate-800'));

  toast.className = `bg-slate-900 border text-slate-100 p-3 px-4 rounded-xl shadow-2xl backdrop-blur-xl text-xs flex items-center gap-2 animate-toast-slide ${borderClass}`;
  
  const icon = type === 'success' ? '✅' : (type === 'error' ? '⚠️' : (type === 'warning' ? '🔔' : 'ℹ️'));
  toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
  
  toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(30px)';
    toast.style.transition = 'all 0.3s cubic-bezier(0.16, 1, 0.3, 1)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// Custom Modal Promise Component Helper
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

// Fetch documents on boot
fetchCorpusDocs();

async function fetchCorpusDocs() {
  try {
    let res;
    try {
      res = await fetch('http://localhost:8080/api/documents');
    } catch (e) {
      res = await fetch('/api/documents');
    }
    if (res.ok) {
      activeCorpusDocs = await res.json();
      renderCorpusList();
    }
  } catch (err) {
    console.error('Failed to fetch corpus documents', err);
  }
}

function renderCorpusList() {
  if (!corpusList) return;
  corpusList.innerHTML = '';
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
        <span class="truncate" title="${docTitle}">${docTitle}</span>
      </div>
      <button class="delete-btn text-slate-500 hover:text-rose-400 hover:bg-rose-500/15 p-1 rounded-lg transition-all" data-title="${docTitle}" title="Delete Document">
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
  const confirmed = await showConfirmModal('Delete Document', `Are you sure you want to delete "${title}" from the knowledge corpus?`);
  if (!confirmed) return;

  try {
    let res;
    try {
      res = await fetch(`http://localhost:8080/api/documents?title=${encodeURIComponent(title)}`, { method: 'DELETE' });
    } catch (e) {
      res = await fetch(`/api/documents?title=${encodeURIComponent(title)}`, { method: 'DELETE' });
    }
    if (res.ok) {
      showToast(`Deleted "${title}"`, 'success');
      appendAssistantMessage(`🗑️ Deleted document "${title}" from vector store and cache.`);
      await fetchCorpusDocs();
    } else {
      showToast(`Failed to delete "${title}"`, 'error');
    }
  } catch (err) {
    showToast('Error deleting document', 'error');
  }
}

// Chat Form Submission
chatForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = userInput.value.trim();
  if (!text) return;

  hideMentionDropdown();
  appendUserMessage(text);
  userInput.value = '';

  const { bubbleDiv, messageContent } = appendAssistantMessage('Thinking...');
  const mode = modeSelect.value;

  try {
    let response;
    try {
      response = await fetch('http://localhost:8080/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: text, retrievalMode: mode })
      });
    } catch (e) {
      response = await fetch('/api/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: text, retrievalMode: mode })
      });
    }

    const data = await response.json();
    bubbleDiv.innerText = data.answer || 'Response received.';

    if (data.retrievedContexts && data.retrievedContexts.length > 0) {
      appendContextDrawer(messageContent, data.retrievedContexts);
    }
  } catch (err) {
    bubbleDiv.innerText = 'Failed to connect to Groundwork backend.';
  }
});

// @ Mention Autocomplete Handling
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
  if (!mentionDropdown.classList.contains('hidden')) {
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

function renderMentionDropdown(matches: string[], atIdx: number) {
  mentionDropdown.innerHTML = '';
  selectedMentionIdx = 0;

  matches.forEach((docTitle, idx) => {
    const item = document.createElement('div');
    item.className = `mention-item flex items-center gap-2 p-2 px-3 rounded-xl text-xs text-slate-300 hover:bg-indigo-500/20 hover:text-white cursor-pointer transition-all ${idx === 0 ? 'bg-indigo-500/20 text-white font-semibold' : ''}`;
    item.innerHTML = `<span>📄</span> <span>${docTitle}</span>`;
    item.addEventListener('click', () => {
      const beforeAt = userInput.value.substring(0, atIdx);
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
  mentionDropdown.classList.add('hidden');
  mentionDropdown.classList.remove('flex');
}

// Wire Suggestion Chips
document.addEventListener('click', (e) => {
  const target = e.target as HTMLElement;
  if (target.classList.contains('chip')) {
    const prompt = target.getAttribute('data-prompt');
    if (prompt) {
      userInput.value = prompt;
      chatForm.dispatchEvent(new Event('submit', { cancelable: true }));
    }
  }
});

reindexBtn.addEventListener('click', async () => {
  try {
    let res;
    try {
      res = await fetch('http://localhost:8080/api/admin/reindex', { method: 'POST' });
    } catch (e) {
      res = await fetch('/api/admin/reindex', { method: 'POST' });
    }
    if (res.status === 409) {
      showToast('Re-index job already in progress', 'warning');
    } else {
      const data = await res.json();
      showToast(`Async re-index triggered! Job ID: ${data.jobId}`, 'success');
    }
  } catch (err) {
    showToast('Error triggering re-index job', 'error');
  }
});

if (attachBtn && fileInput) {
  attachBtn.addEventListener('click', () => fileInput.click());
}

if (sidebarDropzone && fileInput) {
  sidebarDropzone.addEventListener('click', () => fileInput.click());
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

// Drag & Drop File Upload
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

async function processFileUpload(file: File) {
  showToast(`Uploading "${file.name}"...`, 'info');
  const { bubbleDiv } = appendAssistantMessage(`📄 Uploading and indexing "${file.name}"...`);

  const formData = new FormData();
  formData.append('file', file);

  try {
    let res;
    try {
      res = await fetch('http://localhost:8080/api/documents/upload', {
        method: 'POST',
        body: formData
      });
    } catch (e) {
      res = await fetch('/api/documents/upload', {
        method: 'POST',
        body: formData
      });
    }

    const data = await res.json();
    if (res.ok) {
      showToast(`Uploaded "${data.filename}"`, 'success');
      bubbleDiv.innerText = `✅ Successfully uploaded "${data.filename}"! Indexed ${data.chunksIndexed} chunks into vector store. Ask me anything about it!`;
      await fetchCorpusDocs();
    } else {
      showToast(`Upload failed: ${data.error}`, 'error');
      bubbleDiv.innerText = `⚠️ Upload failed: ${data.error}`;
    }
  } catch (err) {
    showToast(`Error uploading "${file.name}"`, 'error');
    bubbleDiv.innerText = `⚠️ Error uploading document "${file.name}".`;
  }
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
  header.innerHTML = `<span>🔍 Inspect ${chunks.length} Retrieved Context Chunks</span> <span>▼</span>`;

  const body = document.createElement('div');
  body.className = 'p-3 flex flex-col gap-2 border-t border-indigo-500/20 hidden';

  chunks.forEach((chunk) => {
    const item = document.createElement('div');
    item.className = 'bg-slate-950 border border-slate-800 p-2.5 rounded-lg text-slate-300 leading-relaxed';
    item.innerHTML = `
      <div class="font-semibold text-cyan-400 mb-1">${chunk.title || 'Document Chunk'} (Score: ${chunk.score ? chunk.score.toFixed(4) : '1.0'})</div>
      <div>${chunk.content}</div>
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
