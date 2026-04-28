// Chess Frontend - WebSocket client & board rendering
// AI-assisted: Board rendering, WebSocket message handling, and DOM manipulation (Gemini)
// Core game logic and move validation handled server-side in Chess.kt
const protocol = location.protocol === "https:" ? "wss" : "ws";
const ws = new WebSocket(`${protocol}://${location.host}/${gameName}`);
window.ws = ws;

let myPlayerIndex = -1; // 0 for White, 1 for Black
let isHost = false;
let currentBoard = "";
let currentTurn = 0;
let selectedSquare = -1;
let legalMovesForSelected = [];

// UI Elements
const elGameArea = document.getElementById("gameArea");
const elGameOver = document.getElementById("gameOverArea");
const boardEl = document.getElementById("boardEl");

// Pieces mapped to reliable Lichess SVG URLs
//
const pieceImages = {
  'K': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/wK.svg', 
  'Q': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/wQ.svg', 
  'R': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/wR.svg', 
  'B': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/wB.svg', 
  'N': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/wN.svg', 
  'P': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/wP.svg',
  'k': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/bK.svg', 
  'q': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/bQ.svg', 
  'r': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/bR.svg', 
  'b': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/bB.svg', 
  'n': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/bN.svg', 
  'p': 'https://raw.githubusercontent.com/lichess-org/lila/master/public/piece/cburnett/bP.svg',
  '.': ''
};

// WebSocket handlers
ws.onmessage = (e) => {
  const msg = JSON.parse(e.data);
  switch (msg.type) {
    case "ROOM_CREATED":
      isHost = true;
      myPlayerIndex = msg.playerIndex;
      showWaitingRoom(msg.roomId || "????");
      document.getElementById("waitingMessage").style.display = "block";
      break;

    case "JOIN_OK":
      myPlayerIndex = msg.playerIndex;
      showWaitingRoom(msg.roomId || "????");
      document.getElementById("waitingMessage").style.display = "block";
      break;

    case "JOIN_FAIL":
      document.getElementById("joinError").innerText = msg.reason;
      document.getElementById("joinError").style.display = "block";
      document.getElementById("lobby").style.display = "block";
      document.getElementById("waitingRoom").style.display = "none";
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
      document.getElementById("playerColorText").innerText = 
        myPlayerIndex === 0 ? "You are White (bottom)" : "You are Black (top)";
      updateGameState(msg);
      break;

    case "STATE":
      selectedSquare = -1;
      legalMovesForSelected = [];
      updateGameState(msg);
      break;

    case "LEGAL_MOVES":
      legalMovesForSelected = msg.moves;
      renderBoard(); // re-render to highlight legal squares
      break;

    case "MOVE_INVALID":
      selectedSquare = -1;
      legalMovesForSelected = [];
      renderBoard();
      break;

    case "GAME_END":
      elGameArea.style.display = "none";
      elGameOver.style.display = "block";
      const winnerText = msg.winner === myPlayerIndex ? "You Win!" : 
                         (msg.winner === -1 ? "Draw!" : "Opponent Wins!");
      document.getElementById("winnerText").innerText = winnerText;
      break;
  }
};

function resign() {
  if (confirm("Are you sure you want to resign?")) {
    ws.send(JSON.stringify({ type: "CHESS_RESIGN" })); // NOT CHANGED TO CHESS_RESIGN YET
  }
}

// Gameplay Action
function handleSquareClick(index) {
  if (currentTurn !== myPlayerIndex) return; // Not our turn

  const piece = currentBoard[index];
  const isWhitePiece = piece >= 'A' && piece <= 'Z';
  const isMyPiece = piece !== '.' && (myPlayerIndex === 0 ? isWhitePiece : !isWhitePiece);

  // If we clicked our own piece, select it and ask server for legal moves
  if (isMyPiece) {
    selectedSquare = index;
    legalMovesForSelected = [];
    renderBoard();
    ws.send(JSON.stringify({ type: "CHESS_MOVES", from: index }));
    return;
  }

  // If we clicked an empty square or enemy piece, and we have a piece selected, try to move
  if (selectedSquare !== -1) {
    ws.send(JSON.stringify({
      type: "CHESS_MOVE",
      from: selectedSquare,
      to: index
    }));
  }
}

// State Rendering
function updateGameState(state) {
  currentBoard = state.board;
  currentTurn = state.turn;
  
  const alertEl = document.getElementById("turnAlert");
  const turnTextEl = document.getElementById("turnText");
  if (currentTurn === myPlayerIndex) {
    alertEl.style.backgroundColor = "rgba(139, 92, 246, 0.2)";
    alertEl.style.borderColor = "var(--gn-primary)";
    turnTextEl.innerText = "It's your turn!";
  } else {
    alertEl.style.backgroundColor = "transparent";
    alertEl.style.borderColor = "#374151";
    turnTextEl.innerText = "Waiting for opponent...";
  }

  renderBoard();
}

function renderBoard() {
  boardEl.innerHTML = "";
  // If we are playing as Black, we flip the board visually.
  const flipBoard = myPlayerIndex === 1;

  for (let i = 0; i < 64; i++) {
    const renderIndex = flipBoard ? 63 - i : i;
    
    const row = Math.floor(renderIndex / 8);
    const col = renderIndex % 8;
    const isLightSquare = (row + col) % 2 === 0;

    const square = document.createElement("div");
    square.className = `chess-square ${isLightSquare ? 'light' : 'dark'}`;
    
    if (renderIndex === selectedSquare) {
      square.classList.add("selected");
    }
    if (legalMovesForSelected.includes(renderIndex)) {
      square.classList.add("legal");
    }

    const pieceStr = currentBoard[renderIndex];
    if (pieceStr !== '.') {
      const img = document.createElement("img");
      img.src = pieceImages[pieceStr];
      img.style.width = "80%";
      img.style.height = "80%";
      img.draggable = false;
      square.appendChild(img);
    }

    square.onclick = () => handleSquareClick(renderIndex);
    boardEl.appendChild(square);
  }
}
