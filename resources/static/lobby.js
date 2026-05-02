document.addEventListener("DOMContentLoaded", () => {
    window.elLobby = document.getElementById("lobby");
    window.elWaitingRoom = document.getElementById("waitingRoom");
    window.elChatContainer = document.getElementById("chatContainer");
    window.elChatMessages = document.getElementById("chatMessages");
    const roomIdInput = document.getElementById("roomIdInput");
    if (roomIdInput) {
        roomIdInput.addEventListener("input", () => {
            roomIdInput.value = roomIdInput.value.replace(/\D/g, "").slice(0, 4);
        });
    }
    const chatInput = document.getElementById("chatInput");
    if (chatInput) {
        chatInput.addEventListener("keydown", (event) => {
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                sendChatMessage();
            }
        });
    }
});

function createRoom() {
    if (!window.ws || window.ws.readyState !== WebSocket.OPEN) return;
    const path = window.location.pathname.split("/").pop();

    const gameMap = {
        "chess": "CHESS",
        "gofish": "GOFISH"
    };

    const game = gameMap[path];
    window.ws.send(JSON.stringify({ type: `CREATE_${game}` }));
}

function joinRoom() {
    if (!window.ws || window.ws.readyState !== WebSocket.OPEN) return;
    const input = document.getElementById("roomIdInput");
    const id = input.value.trim();
    if (!/^\d{4}$/.test(id)) {
        document.getElementById("joinError").innerText = "Enter a 4-digit code";
        document.getElementById("joinError").style.display = "block";
        return;
    }
    document.getElementById("joinError").style.display = "none";
    window.ws.send(JSON.stringify({
        type: "JOIN",
        roomId: id
    }));
}

function showWaitingRoom(roomId) {
    const displayRoomId = document.getElementById("displayRoomId");
    if (displayRoomId) {
        displayRoomId.innerText = roomId;
    } else {
        console.warn("displayRoomId not found");
    }
    if (window.elLobby) {
        window.elLobby.style.display = "none";
    } else {
        console.warn("elLobby not initialized");
    }
    if (window.elWaitingRoom) {
        window.elWaitingRoom.style.display = "block";
    } else {
        console.warn("elWaitingRoom not initialized");
    }
    setChatVisible(true);
}

// TEMPORARILY HARD-CODED FOR CHESS! THIS SHOULUD BE CHANGED AND MOVED TO RESPECTIVE JS FILE
// ALSO RIGHT NOW DOESN'T WORK.........
function startGame() {
    if (!window.ws || window.ws.readyState !== WebSocket.OPEN) return; 
    const game = window.location.pathname.includes("chess") ? "CHESS" : "GOFISH";
    window.ws.send(JSON.stringify({ type: `START_${game}` }));
}

function resign() {
    if (confirm("Are you sure you want to resign?")) {
        window.ws.send(JSON.stringify({ type: "RESIGN" }));
    }
}

function sendChatMessage() {
    if (!window.ws || window.ws.readyState !== WebSocket.OPEN) return;
    const chatInput = document.getElementById("chatInput");
    if (!chatInput) return;
    const text = chatInput.value.trim().slice(0, 280);
    if (!text) return;
    window.ws.send(JSON.stringify({
        type: "CHAT_SEND",
        text
    }));
    chatInput.value = "";
}

function setChatVisible(visible) {
    if (window.elChatContainer) {
        window.elChatContainer.style.display = visible ? "block" : "none";
    }
}

function renderChatMessages(messages) {
    if (!window.elChatMessages) return;
    if (!messages || messages.length === 0) {
        window.elChatMessages.innerHTML = '<div class="chat-empty">No messages yet. Say hello before the match starts.</div>';
        return;
    }

    window.elChatMessages.innerHTML = messages.map(renderChatMessageHtml).join("");
    window.elChatMessages.scrollTop = window.elChatMessages.scrollHeight;
}

function appendChatMessage(message) {
    if (!window.elChatMessages) return;
    const emptyState = window.elChatMessages.querySelector(".chat-empty");
    if (emptyState) {
        emptyState.remove();
    }
    window.elChatMessages.insertAdjacentHTML("beforeend", renderChatMessageHtml(message));
    window.elChatMessages.scrollTop = window.elChatMessages.scrollHeight;
}

function renderChatMessageHtml(message) {
    const author = escapeHtml(message.author || "Unknown player");
    const text = escapeHtml(message.text || "");
    const sentAtLabel = escapeHtml(message.sentAtLabel || "");
    return `
      <article class="chat-message">
        <div class="chat-meta">
          <strong>${author}</strong>
          <span>${sentAtLabel}</span>
        </div>
        <p>${text}</p>
      </article>
    `;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}
