(function() {
    console.log('Chatbot JS initializing...');
    const chatToggleBtn = document.getElementById('chat-toggle-btn');
    const chatWindow = document.getElementById('chat-window');
    const chatCloseBtn = document.getElementById('chat-close-btn');
    const chatInput = document.getElementById('chat-input');
    const chatSendBtn = document.getElementById('chat-send-btn');
    const chatMessages = document.getElementById('chat-messages');
    
    // Determine context (Campaign vs Activity)
    const campaignIdEl = document.getElementById('campaignId');
    const activityIdEl = document.getElementById('activityId');
    
    let contextType = null;
    let contextId = null;

    if (campaignIdEl) {
        contextType = 'campaign';
        contextId = campaignIdEl.value;
        console.log('Chatbot Context: Campaign ' + contextId);
    } else if (activityIdEl) {
        contextType = 'activity';
        contextId = activityIdEl.value;
        console.log('Chatbot Context: Activity ' + contextId);
    } else {
        console.warn('Chatbot: No campaignId or activityId found. Chatbot disabled.');
        if (chatToggleBtn) chatToggleBtn.style.display = 'none';
        return;
    }

    let isChatOpen = false;

    function toggleChat() {
        isChatOpen = !isChatOpen;
        if (isChatOpen) {
            chatWindow.classList.remove('hidden');
            // Animation delay
            setTimeout(() => {
                chatWindow.classList.remove('scale-95', 'opacity-0');
                chatWindow.classList.add('scale-100', 'opacity-100');
            }, 10);
            chatInput.focus();
        } else {
            chatWindow.classList.remove('scale-100', 'opacity-100');
            chatWindow.classList.add('scale-95', 'opacity-0');
            setTimeout(() => {
                chatWindow.classList.add('hidden');
            }, 300);
        }
    }

    if (chatToggleBtn) chatToggleBtn.addEventListener('click', toggleChat);
    if (chatCloseBtn) chatCloseBtn.addEventListener('click', toggleChat);

    async function sendMessage() {
        const message = chatInput.value.trim();
        if (!message) return;

        // 1. Add User Message
        addMessage(message, 'user');
        chatInput.value = '';

        // 2. Show Loading
        const loadingId = addLoadingMessage();

        try {
            // Call API dynamically based on context
            let apiUrl;
            if (contextType === 'campaign' && contextId === 'global') { // Special handling for global dashboard
                apiUrl = `/api/v1/dashboard/global/query`;
            } else {
                apiUrl = `/api/v1/dashboard/${contextType}/${contextId}/query`;
            }
            console.log('Sending query to:', apiUrl);
            
            const response = await fetch(apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    query: message
                })
            });

            const data = await response.json();

            // 3. Remove Loading & Add Bot Response
            removeMessage(loadingId);
            
            if (data.answer) {
                addMessage(data.answer, 'bot');
            } else {
                addMessage('죄송합니다. 답변을 생성하지 못했습니다.', 'bot');
            }

        } catch (error) {
            console.error('Chat Error:', error);
            removeMessage(loadingId);
            addMessage('오류가 발생했습니다. 잠시 후 다시 시도해주세요.', 'bot');
        }
    }

    if (chatSendBtn) chatSendBtn.addEventListener('click', sendMessage);
    
    if (chatInput) {
        chatInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') sendMessage();
        });
    }

    function addMessage(text, sender) {
        const row = document.createElement('div');
        row.className = `flex justify-${sender === 'user' ? 'end' : 'start'}`;

        const bubble = document.createElement('div');
        bubble.className = sender === 'user'
            ? 'bg-blue-600 text-white rounded-lg py-2 px-4 max-w-[85%] shadow-sm text-sm'
            : 'bg-white border border-gray-200 rounded-lg py-2 px-4 max-w-[85%] shadow-sm text-sm text-gray-800 whitespace-pre-wrap';

        // Format bot messages with simple markdown-like rendering
        if (sender === 'bot') {
            bubble.innerHTML = formatBotMessage(text);
        } else {
            bubble.textContent = text;
        }

        row.appendChild(bubble);
        chatMessages.appendChild(row);
        scrollToBottom();
    }

    function formatBotMessage(text) {
        // 1. Escape HTML first
        let formatted = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // 2. Bold: **text** → <strong>text</strong>
        formatted = formatted.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');

        // 3. Lists: - item → • item
        formatted = formatted.replace(/^- (.+)$/gm, '• $1');

        // 4. Numbers: 1. item → 1. item (keep as is but add spacing)
        formatted = formatted.replace(/^(\d+)\. (.+)$/gm, '<strong>$1.</strong> $2');

        // 5. Section headers (lines starting with #)
        formatted = formatted.replace(/^### (.+)$/gm, '<strong style="font-size: 1.1em;">$1</strong>');
        formatted = formatted.replace(/^## (.+)$/gm, '<strong style="font-size: 1.2em;">$1</strong>');
        formatted = formatted.replace(/^# (.+)$/gm, '<strong style="font-size: 1.3em;">$1</strong>');

        // 6. Line breaks: \n\n → paragraph break, \n → line break
        formatted = formatted.replace(/\n\n/g, '<br><br>');
        formatted = formatted.replace(/\n/g, '<br>');

        // 7. Horizontal rules: ━━━ or ---
        formatted = formatted.replace(/^[━─]{3,}$/gm, '<hr style="border-top: 1px solid #e5e7eb; margin: 0.5rem 0;">');

        return formatted;
    }

    function addLoadingMessage() {
        const id = 'msg-' + Date.now();
        const row = document.createElement('div');
        row.id = id;
        row.className = 'flex justify-start';
        row.innerHTML = `
            <div class="bg-white border border-gray-200 rounded-lg py-2 px-4 shadow-sm text-sm text-gray-500 flex items-center gap-2">
                <i class="fa-solid fa-circle-notch fa-spin text-blue-500"></i>
                <span>분석 중...</span>
            </div>
        `;
        chatMessages.appendChild(row);
        scrollToBottom();
        return id;
    }

    function removeMessage(id) {
        const el = document.getElementById(id);
        if (el) el.remove();
    }

    function scrollToBottom() {
        if (chatMessages) chatMessages.scrollTop = chatMessages.scrollHeight;
    }
})();
