// Go Fish Frontend - WebSocket client
// AI-assisted: UI rendering logic and WebSocket message handling (Gemini 2.5 Pro)
const protocol = location.protocol === "https:" ? "wss" : "ws";
const gameName = window.location.pathname.split("/").pop();
const ws = new WebSocket(`${protocol}://${location.host}/${gameName}`);
window.ws = ws;

let myPlayerIndex = -1;
let isHost = false;

// UI Elements
const elLobby = document.getElementById("lobby");
const elWaitingRoom = document.getElementById("waitingRoom");
const elGameArea = document.getElementById("gameArea");
const elGameOver = document.getElementById("gameOverArea");

// WebSocket handlers
ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  switch (msg.type) {
    case "ROOM_CREATED":
      isHost = true;
      myPlayerIndex = msg.playerIndex;
      showWaitingRoom(msg.roomId);
      break;

    case "JOIN_OK":
      myPlayerIndex = msg.playerIndex;
      break;

    case "JOIN_FAIL":
      document.getElementById("joinError").innerText = msg.reason;
      document.getElementById("joinError").style.display = "block";
      break;

    case "PLAYER_UPDATE":
      if (document.getElementById("playerCount")) {
        document.getElementById("playerCount").innerText = msg.count;
        if (isHost && msg.count >= 2) {
          document.getElementById("btnStart").disabled = false;
          document.getElementById("btnStart").style.display = "block";
        }
      }
      break;

    case "START":
      elLobby.style.display = "none";
      elWaitingRoom.style.display = "none";
      elGameArea.style.display = "block";
      updateGameState(msg);
      break;

    case "STATE":
      updateGameState(msg);
      break;

    case "ASK_RESULT":
      showActionResult(msg.success);
      updateGameState(msg);
      break;

    case "GAME_END":
      elGameArea.style.display = "none";
      elGameOver.style.display = "block";
      const winnerText = msg.winner === myPlayerIndex ? "You Win!" :
                         (msg.winner === -1 ? "It's a tie!" : `Player ${msg.winner + 1} Wins!`);
      document.getElementById("winnerText").innerText = winnerText;
      break;
  }
};

// Gameplay Actions
function askForCard() {
  const target = document.getElementById("askPlayerSelect").value;
  const rank = document.getElementById("askRankSelect").value;
  if (target === "" || rank === "") return;

  ws.send(JSON.stringify({
    type: "GOFISH_ASK",
    target: parseInt(target),
    rank: rank
  }));
}

function endTurn() {
  ws.send(JSON.stringify({ type: "GOFISH_END_TURN" }));
}

function showActionResult(success) {
  const resEl = document.getElementById("actionResult");
  resEl.style.display = "block";
  if (success) {
    resEl.style.color = "#34d399";
    resEl.innerText = "Match found! They handed over their cards. You get another turn.";
    document.getElementById("btnEndTurn").style.display = "none";
  } else {
    resEl.style.color = "#fca5a5";
    resEl.innerText = "Go Fish! You drew a card.";
    document.getElementById("btnAsk").disabled = true;
    document.getElementById("btnEndTurn").style.display = "inline-block";
  }
}

function joinRoom() {
  const roomId = document.getElementById("roomIdInput").value;

  ws.send(JSON.stringify({
    type: "JOIN",
    roomId: roomId
  }));
}

function startGame() {
  ws.send(JSON.stringify({
    type: "START_GO_FISH"
  }));
}

// State Rendering
function updateGameState(state) {
  const isMyTurn = state.turn === myPlayerIndex;

  // Update turn alert
  const alertEl = document.getElementById("turnAlert");
  const turnTextEl = document.getElementById("turnText");
  if (isMyTurn) {
    alertEl.style.backgroundColor = "rgba(139, 92, 246, 0.2)";
    alertEl.style.borderColor = "var(--gn-primary)";
    turnTextEl.innerText = "It's your turn!";
  } else {
    alertEl.style.backgroundColor = "transparent";
    alertEl.style.borderColor = "#374151";
    turnTextEl.innerText = `Waiting for Player ${state.turn + 1}'s turn...`;
  }
  document.getElementById("deckSizeText").innerText = `Deck: ${state.deckSize} cards remaining`;

  // Update action area
  const actionArea = document.getElementById("actionArea");
  if (isMyTurn) {
    actionArea.style.display = "block";
    document.getElementById("btnAsk").disabled = false;
    document.getElementById("btnEndTurn").style.display = "none";

    // Clear old result unless we just had an ask result
    if (state.type !== "ASK_RESULT") {
      document.getElementById("actionResult").style.display = "none";
    }

    // Populate rank dropdown based on current hand
    const rankSelect = document.getElementById("askRankSelect");
    const uniqueRanks = [...new Set(state.myHandRanks)];
    rankSelect.innerHTML = "<option value='' disabled selected>-- Select a rank --</option>";
    uniqueRanks.forEach(r => {
      rankSelect.innerHTML += `<option value="${r}">${r}</option>`;
    });

    // Populate target dropdown
    const targetSelect = document.getElementById("askPlayerSelect");
    targetSelect.innerHTML = "<option value='' disabled selected>-- Select player --</option>";
    state.handSizes.forEach((size, i) => {
      if (i !== myPlayerIndex) {
        targetSelect.innerHTML += `<option value="${i}">Player ${i + 1} (${size} cards)</option>`;
      }
    });
  } else {
    actionArea.style.display = "none";
  }

  // Render my hand
  const handDiv = document.getElementById("myHand");
  handDiv.innerHTML = "";
  state.myHand.forEach(url => {
    handDiv.innerHTML += `<img src="${url}" class="card-image" alt="card" style="width:80px;">`;
  });
  document.getElementById("yourBooksText").innerText = `Books completed: ${state.books[myPlayerIndex]}`;

  // Render opponents
  const oppGrid = document.getElementById("opponentsGrid");
  oppGrid.innerHTML = "";
  state.handSizes.forEach((size, i) => {
    if (i === myPlayerIndex) return;
    const isTheirTurn = state.turn === i;
    const activeClass = isTheirTurn ? "active-turn" : "";

    oppGrid.innerHTML += `
      <div class="player-box ${activeClass}">
        <h4 style="margin:0 0 0.5rem;">Player ${i + 1}</h4>
        <p style="margin:0;font-size:0.9rem;color:#9ca3af;">${size} cards in hand</p>
        <p style="margin:0.25rem 0 0;font-size:0.9rem;color:var(--gn-accent);">Books: ${state.books[i]}</p>
      </div>
    `;
  });
}
