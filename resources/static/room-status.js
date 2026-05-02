document.addEventListener("DOMContentLoaded", () => {
    const section = document.getElementById("roomStatusSection");
    const list = document.getElementById("roomStatusList");
    const empty = document.getElementById("roomStatusEmpty");

    if (!section || !list || !empty) {
        return;
    }

    const protocol = location.protocol === "https:" ? "wss" : "ws";
    let socket = null;
    let reconnectTimer = null;

    const connect = () => {
        socket = new WebSocket(`${protocol}://${location.host}/room-status`);

        socket.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            if (msg.type !== "ROOM_STATUS_LIST") return;
            renderRooms(msg.rooms || []);
        };

        socket.onclose = () => {
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
            }
            reconnectTimer = window.setTimeout(connect, 1500);
        };
    };

    const renderRooms = (rooms) => {
        if (!rooms.length) {
            list.innerHTML = "";
            list.style.display = "none";
            empty.style.display = "block";
            return;
        }

        list.innerHTML = rooms.map(renderRoomCard).join("");
        list.style.display = "grid";
        empty.style.display = "none";
    };

    const renderRoomCard = (room) => {
        return `
          <article class="card room-status-card">
            <div class="room-status-top">
              <span class="status-pill ${escapeHtml(room.statusTone)}">${escapeHtml(room.statusLabel)}</span>
              <strong style="color:white;">${escapeHtml(room.gameName)}</strong>
            </div>
            <div class="room-status-code">Room ${escapeHtml(room.roomId)}</div>
            <p style="margin:0;color:#d1d5db;">${escapeHtml(room.playerSummary)}</p>
            <p style="margin:0;color:#9ca3af;">Host: ${escapeHtml(room.hostUsername)}</p>
            <p style="margin:0;color:#9ca3af;">Players: ${escapeHtml(room.playerNamesDisplay)}</p>
            <p style="margin:0;color:#6b7280;font-size:0.9rem;">${escapeHtml(room.updatedAtLabel)}</p>
          </article>
        `;
    };

    const escapeHtml = (value) => {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    };

    connect();
});
