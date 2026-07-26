const messageList = document.getElementById('messageList') as HTMLDivElement;
const chatForm = document.getElementById('chatForm') as HTMLFormElement;
const userInput = document.getElementById('userInput') as HTMLInputElement;
const modeSelect = document.getElementById('retrievalModeSelect') as HTMLSelectElement;
const reindexBtn = document.getElementById('reindexBtn') as HTMLButtonElement;

chatForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = userInput.value.trim();
  if (!text) return;

  appendMessage('user', text);
  userInput.value = '';

  const assistantBubble = appendMessage('assistant', 'Thinking...');
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
    assistantBubble.innerText = data.answer || 'Response received.';
  } catch (err) {
    assistantBubble.innerText = 'Failed to connect to Groundwork backend.';
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

const uploadBtn = document.getElementById('uploadBtn') as HTMLButtonElement;
const fileInput = document.getElementById('fileInput') as HTMLInputElement;

if (uploadBtn && fileInput) {
  uploadBtn.addEventListener('click', () => fileInput.click());

  fileInput.addEventListener('change', async () => {
    const file = fileInput.files?.[0];
    if (!file) return;

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
        alert(`✅ Uploaded "${data.filename}"! Indexed ${data.chunksIndexed} chunks into vector store.`);
      } else {
        alert(`⚠️ Upload failed: ${data.error}`);
      }
    } catch (err) {
      alert('Error uploading document.');
    } finally {
      fileInput.value = '';
    }
  });
}

function appendMessage(sender: 'user' | 'assistant', text: string): HTMLDivElement {
  const msgDiv = document.createElement('div');
  msgDiv.className = `message ${sender}`;

  const avatarDiv = document.createElement('div');
  avatarDiv.className = 'avatar';
  avatarDiv.innerText = sender === 'user' ? 'YOU' : 'AI';

  const bubbleDiv = document.createElement('div');
  bubbleDiv.className = 'bubble';
  bubbleDiv.innerText = text;

  msgDiv.appendChild(avatarDiv);
  msgDiv.appendChild(bubbleDiv);
  messageList.appendChild(msgDiv);
  messageList.scrollTop = messageList.scrollHeight;

  return bubbleDiv;
}
