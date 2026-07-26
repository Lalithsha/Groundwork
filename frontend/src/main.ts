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
  toast.className = `toast ${type}`;
  
  const icon = type === 'success' ? '✅' : (type === 'error' ? '⚠️' : (type === 'warning' ? '🔔' : 'ℹ️'));
  toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
  
  toastContainer.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(30px)';
    toast.style.transition = 'all 0.3s ease';
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
    corpusList.innerHTML = '<div style="font-size: 0.8rem; color: #94a3b8; padding: 4px;">No documents uploaded yet</div>';
    return;
  }
  activeCorpusDocs.forEach((docTitle) => {
    const item = document.createElement('div');
    item.className = 'corpus-item';
    item.innerHTML = `
      <div class="corpus-info">
        <span class="file-icon">📄</span>
        <span class="file-name" title="${docTitle}">${docTitle}</span>
      </div>
      <button class="delete-btn" data-title="${docTitle}" title="Delete Document">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>
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
    item.className = `mention-item ${idx === 0 ? 'selected' : ''}`;
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
}

function updateMentionHighlight(items: NodeListOf<Element>) {
  items.forEach((item, idx) => {
    if (idx === selectedMentionIdx) {
      item.classList.add('selected');
    } else {
      item.classList.remove('selected');
    }
  });
}

function hideMentionDropdown() {
  mentionDropdown.classList.add('hidden');
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
  msgDiv.className = 'message user';

  const avatarDiv = document.createElement('div');
  avatarDiv.className = 'avatar';
  avatarDiv.innerText = 'YOU';

  const bubbleDiv = document.createElement('div');
  bubbleDiv.className = 'bubble';
  bubbleDiv.innerText = text;

  msgDiv.appendChild(avatarDiv);
  msgDiv.appendChild(bubbleDiv);
  messageList.appendChild(msgDiv);
  messageList.scrollTop = messageList.scrollHeight;

  return bubbleDiv;
}

function appendAssistantMessage(text: string): { bubbleDiv: HTMLDivElement; messageContent: HTMLDivElement } {
  const msgDiv = document.createElement('div');
  msgDiv.className = 'message assistant';

  const avatarDiv = document.createElement('div');
  avatarDiv.className = 'avatar';
  avatarDiv.innerText = 'AI';

  const messageContent = document.createElement('div');
  messageContent.className = 'message-content';

  const bubbleDiv = document.createElement('div');
  bubbleDiv.className = 'bubble';
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
  drawer.className = 'context-drawer';

  const header = document.createElement('div');
  header.className = 'context-header';
  header.innerHTML = `<span>🔍 Inspect ${chunks.length} Retrieved Context Chunks</span> <span>▼</span>`;

  const body = document.createElement('div');
  body.className = 'context-body';
  body.style.display = 'none';

  chunks.forEach((chunk) => {
    const item = document.createElement('div');
    item.className = 'context-item';
    item.innerHTML = `
      <div class="context-item-title">${chunk.title || 'Document Chunk'} (Score: ${chunk.score ? chunk.score.toFixed(4) : '1.0'})</div>
      <div>${chunk.content}</div>
    `;
    body.appendChild(item);
  });

  header.addEventListener('click', () => {
    const isOpen = body.style.display !== 'none';
    body.style.display = isOpen ? 'none' : 'flex';
    header.querySelector('span:last-child')!.textContent = isOpen ? '▼' : '▲';
  });

  drawer.appendChild(header);
  drawer.appendChild(body);
  container.appendChild(drawer);
}
