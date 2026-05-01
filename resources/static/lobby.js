document.addEventListener("DOMContentLoaded", () => {
    window.elLobby = document.getElementById("lobby");
    window.elWaitingRoom = document.getElementById("waitingRoom");
    const roomIdInput = document.getElementById("roomIdInput");
    if (roomIdInput) {
        roomIdInput.addEventListener("input", () => {
            roomIdInput.value = roomIdInput.value.replace(/\D/g, "").slice(0, 4);
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
