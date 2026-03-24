# Games Night Wiki Templates

This directory contains templates to help you fill out the GitHub Wiki for your COMP2850 project submission.

## 1. Personas Template
**Persona 1: Casual Gamer**
*   **Name:** Alex
*   **Age:** 21
*   **Background:** University student looking for a quick break between classes.
*   **Needs/Goals:** Wants a fast, lag-free way to jump into a game of Go Fish with friends without downloading anything.
*   **Pain Points:** Dislikes complicated sign-ups and confusing UI.

**Persona 2: Strategy Enthusiast**
*   **Name:** Sarah
*   **Age:** 28
*   **Background:** Software developer who likes playing chess in her free time.
*   **Needs/Goals:** Wants a reliable chess game online to test strategies. Cares about game integrity and tracking match stats (Leaderboards).
*   **Pain Points:** Irritated by bugs where pieces don't move correctly or game states desync.

## 2. Job Stories
1.  **When** I am bored at home, **I want to** quickly create a private room for Go Fish, **So that** I can share the 4-digit code with my friends and start playing immediately.
2.  **When** signing up for an account, **I want to** know my password is secure, **So that** my profile and leaderboard stats aren't compromised.
3.  **When** playing Chess, **I want to** easily see my legal moves highlighted when I click a piece, **So that** I don't have to guess or make illegal moves.

## 3. Retrospective Example
**What went well:**
*   Setting up the real-time WebSocket communication layer was highly efficient.
*   Refactoring our database to use HMAC-SHA256 and secure UUIDs provided a huge security boost over the initial insecure plain text implementation.
*   The Amper build system and JUnit testing made CI/CD very straightforward.

**What could be improved:**
*   Initial logic synchronization for Go Fish player states took longer than expected due to hidden card logic.
*   We could improve mobile responsiveness for the Chess board viewing grid.

## 4. Evaluation Strategy
*   **Unit Testing:** We wrote comprehensive unit tests for `Deck`, `Card`, and `Chess` logic to cover boundary cases.
*   **Manual End-to-End:** Verified room creation, joining (handling duplicate players, full rooms, game in-progress blocks), and user logout lifecycle across two browsers (Chrome/Firefox).
*   **Linter/CI:** Configured GitHub actions to prevent regressions.
