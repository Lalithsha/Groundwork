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

interface DocumentChunk {
  id: string;
  title: string;
  content: string;
  sourceType: string;
  score: number;
}

chatForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = userInput.value.trim();
  if (!text) return;

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
      alert('⚠️ Re-index job already in progress!');
    } else {
      const data = await res.json();
      alert(`⚡ Async re-index triggered! Job ID: ${data.jobId}`);
    }
  } catch (err) {
    alert('Error triggering re-index job.');
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
      bubbleDiv.innerText = `✅ Successfully uploaded "${data.filename}"! Indexed ${data.chunksIndexed} chunks into vector store. Ask me anything about it!`;
      addCorpusItem(data.filename);
    } else {
      bubbleDiv.innerText = `⚠️ Upload failed: ${data.error}`;
    }
  } catch (err) {
    bubbleDiv.innerText = `⚠️ Error uploading document "${file.name}".`;
  }
}

function addCorpusItem(filename: string) {
  if (!corpusList) return;
  const item = document.createElement('div');
  item.className = 'corpus-item';
  item.innerHTML = `<span class="file-icon">📄</span><span class="file-name">${filename}</span>`;
  corpusList.prepend(item);
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
