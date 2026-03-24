# COMP2850: Games Night
A fully functional multiplayer game center built with Kotlin, Ktor, and WebSockets.

## 🚀 Overview
**Games Night** is an online web application where users can create secure accounts, host game lobbies, and play real-time multiplayer board and card games natively in their browser. 

The application currently supports:
1. **Chess** (2 Players) - Fully implemented strict rules including check, checkmate, castling, pawn promotion, and en-passant tracking!
2. **Go Fish** (2+ Players) - Track books, draw from the pond, and intelligently query opponents for matching cards.

## 🌟 Key Features
* **Real-time WebSockets**: Every move, card draw, and lobby join is instantly broadcasted to clients in the exact same room, ensuring low-latency synchronization without page refreshes.
* **Complex Backend Engines**: The server handles all game logic deterministically. For example, `Chess.kt` calculates pseudo-legal geometry, dry-runs moves on temporary boards to strictly block moves resulting in checks, and evaluates edge-cases like Checkmate and Stalemate.
* **Modern Custom UI**: Powered by Pebble templates and PicoCSS, augmented with our own `style.css` containing dark-mode aesthetics, responsive card containers, and vector UI elements (Lichess SVGs).
* **Secure Authentication**: User passwords are mathematically transformed using cryptographically secure `HMAC-SHA256` hashing and a static secret salt. Session identifiers are strictly decoupled from easily predictable attributes by utilizing `UUID.randomUUID()` values stored in secure cookies (`Max-Age`, `Path=/`).
* **Game History Leaderboard**: A persistence system mapped via Jetbrains Exposed ORM tracking every win and draw across users, calculating Global Rankings.

## 🛠️ Tech Stack
- **Backend**: Kotlin, Ktor 3.0, Amper Build Tool
- **Frontend**: HTML5, Vanilla JavaScript, CSS3, PicoCSS, Pebble Templates
- **Database**: H2 (Development & Testing) / PostgreSQL (Production ready), Exposed JDBC ORM
- **Testing**: JUnit 5, Amper test runner

## 🏃 Building & Running
To run the server locally on your machine, simply execute the following in your terminal:

```bash
# Run the Ktor Server natively (Port 8080)
./amper run

# Execute the Backend Logic Unit Tests
./amper test
```

Once running, open your browser and navigate to **[http://localhost:8080](http://localhost:8080)**.

## 🗄️ Database Inspection (H2)
During development, we persist data to a local `.mv.db` file located in `./build/db/games-night`.
To inspect the database:
1. Start the server once to generate the DB file.
2. Stop the server (`Ctrl+C`).
3. View the SQLite/H2 data directly using your IDE's Database tool or the native H2 Console by connecting to JDBC URL `jdbc:h2:./build/db/games-night;MODE=PostgreSQL`.

---
*Created as part of the COMP2850 Assessment.*
