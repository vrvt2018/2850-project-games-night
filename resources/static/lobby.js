const gameName = window.location.pathname.split("/").pop();
const ws = new WebSocket(`ws://${location.host}/${gameName}`);

function createRoom() {
    const game = window.location.pathname.split("/").pop().toUpperCase();
    ws.send(JSON.stringify({ type: `CREATE_${game}` }));
}

function joinRoom() {
    const input = document.getElementById("roomIdInput");
    const id = input.value.trim();
    if (id.length !== 4) {
        document.getElementById("joinError").innerText = "Enter a 4-digit code";
        document.getElementById("joinError").style.display = "block";
        return;
    }
    document.getElementById("joinError").style.display = "none";
    ws.send(JSON.stringify({
        type: "JOIN",
        roomId: id
    }));
}

function showWaitingRoom(roomId) {
    document.getElementById("displayRoomId").innerText = roomId;
    elLobby.style.display = "none";
    elWaitingRoom.style.display = "block";
}

// TEMPORARILY HARD-CODED FOR CHESS! THIS SHOULUD BE CHANGED AND MOVED TO RESPECTIVE JS FILE
// ALSO RIGHT NOW DOESN'T WORK.........
function startGame() {
    const game = window.location.pathname.includes("chess") ? "CHESS" : "GOFISH";
    ws.send(JSON.stringify({ type: `START_${game}` }));
}

function resign() {
    if (confirm("Are you sure you want to resign?")) {
        ws.send(JSON.stringify({ type: "RESIGN" }));
    }
}