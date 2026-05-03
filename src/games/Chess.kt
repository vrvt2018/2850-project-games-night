// AI-assisted: Move validation dry-run approach, castling rights tracking, and checkmate detection (Gemini)
// Server-side chess engine: validates moves, detects check/checkmate/stalemate
package com.example.games

/**
 * Chess game implementation for 2 players using a standard 8×8 board.
 *
 * The board is represented as a flat array of 64 squares (indices 0–63).
 * Index 0 = a8 (top-left from White's perspective), index 63 = h1 (bottom-right).
 * Piece encoding: uppercase = White (e.g. 'K'), lowercase = Black (e.g. 'k').
 *
 * Standard initial layout:
 *   Row 0 (rank 8): r n b q k b n r   (Black pieces)
 *   Row 1 (rank 7): p p p p p p p p   (Black pawns)
 *   Rows 2–5 (ranks 6–3): empty
 *   Row 6 (rank 2): P P P P P P P P   (White pawns)
 *   Row 7 (rank 1): R N B Q K B N R   (White pieces)
 *
 * Piece key:
 *   K/k = King, Q/q = Queen, R/r = Rook, B/b = Bishop, N/n = Knight, P/p = Pawn
 */
class Chess(
    name: String = "Chess",
) : Game(name, 2, 2) {
    /** Flat 64-element board. '.' means empty square. */
    private val board: CharArray = CharArray(64) { '.' }

    /**
     * Player indices: 0 = White, 1 = Black.
     * White moves first (turn = 0).
     */
    private var turn: Int = 0

    /** Set to the winner index (0 or 1) when a king is captured, -1 for draw. */
    private var winner: Int = -2 // -2 = game not over

    // Castling rights
    private var whiteKingMoved = false
    private var blackKingMoved = false
    private var whiteRookAMoved = false // a1 (56)
    private var whiteRookHMoved = false // h1 (63)
    private var blackRookAMoved = false // a8 (0)
    private var blackRookHMoved = false // h8 (7)

    // En passant target square (0-63), or -1 if none
    private var enPassantTarget = -1

    // ─────────────────────────────────────────────────────────────────────────
    // Game lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun addPlayer(): Boolean {
        if (numPlayers >= maxPlayers) return false
        numPlayers++
        return true
    }

    override fun startGame() {
        val backRow = charArrayOf('r', 'n', 'b', 'q', 'k', 'b', 'n', 'r')
        for (col in 0..7) board[col] = backRow[col]
        for (col in 0..7) board[8 + col] = 'p'
        for (i in 16..47) board[i] = '.'
        for (col in 0..7) board[48 + col] = 'P'
        for (col in 0..7) board[56 + col] = backRow[col].uppercaseChar()

        whiteKingMoved = false
        blackKingMoved = false
        whiteRookAMoved = false
        whiteRookHMoved = false
        blackRookAMoved = false
        blackRookHMoved = false
        enPassantTarget = -1
        started = true
        winner = -2
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Move logic
    // ─────────────────────────────────────────────────────────────────────────

    fun makeMove(
        from: Int,
        to: Int,
    ): Boolean {
        if (from !in 0..63 || to !in 0..63 || from == to) return false
        val piece = board[from]
        if (piece == '.') return false
        if (isWhitePiece(piece) != (turn == 0)) return false

        val target = board[to]
        if (target != '.' && isWhitePiece(target) == isWhitePiece(piece)) return false

        if (!isMoveLegalStrict(from, to)) return false

        // Apply move
        board[to] = piece
        board[from] = '.'

        // Handle Castling Execution (King moved 2 squares)
        if (piece.lowercaseChar() == 'k' && kotlin.math.abs(from - to) == 2) {
            if (to == 62) {
                board[61] = 'R'
                board[63] = '.'
            } // White Kingside
            else if (to == 58) {
                board[59] = 'R'
                board[56] = '.'
            } // White Queenside
            else if (to == 6) {
                board[5] = 'r'
                board[7] = '.'
            } // Black Kingside
            else if (to == 2) {
                board[3] = 'r'
                board[0] = '.'
            } // Black Queenside
        }

        // Handle En Passant Execution
        if (piece.lowercaseChar() == 'p' && to == enPassantTarget) {
            val captureIdx = if (isWhitePiece(piece)) to + 8 else to - 8
            board[captureIdx] = '.'
        }

        // Set En Passant Target for NEXT turn if pawn double step
        enPassantTarget = -1
        if (piece.lowercaseChar() == 'p' && kotlin.math.abs(from - to) == 16) {
            enPassantTarget = if (isWhitePiece(piece)) from - 8 else from + 8
        }

        // Pawn promotion (auto-promote to queen)
        if (piece == 'P' && to < 8) board[to] = 'Q'
        if (piece == 'p' && to >= 56) board[to] = 'q'

        // Update castling rights
        if (piece == 'K') whiteKingMoved = true
        if (piece == 'k') blackKingMoved = true
        if (from == 56 || target == 'R' && to == 56) whiteRookAMoved = true
        if (from == 63 || target == 'R' && to == 63) whiteRookHMoved = true
        if (from == 0 || target == 'r' && to == 0) blackRookAMoved = true
        if (from == 7 || target == 'r' && to == 7) blackRookHMoved = true

        // Advance turn
        turn = 1 - turn

        // After turn changes, check checkmate/stalemate
        if (!hasLegalMoves(turn)) {
            val kingChar = if (turn == 0) 'K' else 'k'
            val kingPos = board.indexOfFirst { it == kingChar }
            val inCheck = if (kingPos != -1) isAttacked(kingPos, turn != 0, board) else true
            if (inCheck) {
                winner = 1 - turn
            } else {
                winner = -1
            }
        }

        return true
    }

    private fun isMoveLegalStrict(
        from: Int,
        to: Int,
    ): Boolean {
        if (!makeMoveDryRun(from, to, board, enPassantTarget)) return false

        val tempBoard = board.copyOf()
        val piece = tempBoard[from]

        tempBoard[to] = piece
        tempBoard[from] = '.'

        // Apply Castling on temp board
        if (piece.lowercaseChar() == 'k' && kotlin.math.abs(from - to) == 2) {
            if (to == 62) {
                tempBoard[61] = 'R'
                tempBoard[63] = '.'
            } else if (to == 58) {
                tempBoard[59] = 'R'
                tempBoard[56] = '.'
            } else if (to == 6) {
                tempBoard[5] = 'r'
                tempBoard[7] = '.'
            } else if (to == 2) {
                tempBoard[3] = 'r'
                tempBoard[0] = '.'
            }
        }

        // Apply En Passant on temp board
        if (piece.lowercaseChar() == 'p' && to == enPassantTarget) {
            val captureIdx = if (isWhitePiece(piece)) to + 8 else to - 8
            tempBoard[captureIdx] = '.'
        }

        val isWhite = turn == 0
        val kingChar = if (isWhite) 'K' else 'k'
        val kingPos = tempBoard.indexOfFirst { it == kingChar }

        return kingPos != -1 && !isAttacked(kingPos, !isWhite, tempBoard)
    }

    private fun isAttacked(
        target: Int,
        byWhite: Boolean,
        checkBoard: CharArray,
    ): Boolean {
        for (i in 0..63) {
            val p = checkBoard[i]
            if (p == '.') continue
            if (isWhitePiece(p) == byWhite) {
                if (p.lowercaseChar() == 'p') {
                    val fromRow = i / 8
                    val fromCol = i % 8
                    val toRow = target / 8
                    val toCol = target % 8
                    val dr = toRow - fromRow
                    val dc = toCol - fromCol
                    val dir = if (byWhite) -1 else 1
                    if (dr == dir && kotlin.math.abs(dc) == 1) return true
                } else {
                    if (isLegalMove(p, i, target, checkBoard, -1)) return true
                }
            }
        }
        return false
    }

    private fun hasLegalMoves(playerIndex: Int): Boolean {
        for (i in 0..63) {
            val p = board[i]
            if (p != '.' && (isWhitePiece(p) == (playerIndex == 0))) {
                val moves = legalMovesFrom(i)
                if (moves.isNotEmpty()) return true
            }
        }
        return false
    }

    private fun isLegalMove(
        piece: Char,
        from: Int,
        to: Int,
        checkBoard: CharArray,
        epTarget: Int,
    ): Boolean {
        val fromRow = from / 8
        val fromCol = from % 8
        val toRow = to / 8
        val toCol = to % 8
        val dr = toRow - fromRow
        val dc = toCol - fromCol

        return when (piece.lowercaseChar()) {
            'p' -> {
                isLegalPawnMove(piece, fromRow, fromCol, toRow, toCol, dr, dc, checkBoard, epTarget)
            }

            'r' -> {
                isLegalRookMove(fromRow, fromCol, toRow, toCol, dr, dc, checkBoard)
            }

            'n' -> {
                isLegalKnightMove(dr, dc)
            }

            'b' -> {
                isLegalBishopMove(fromRow, fromCol, toRow, toCol, dr, dc, checkBoard)
            }

            'q' -> {
                isLegalRookMove(fromRow, fromCol, toRow, toCol, dr, dc, checkBoard) ||
                    isLegalBishopMove(fromRow, fromCol, toRow, toCol, dr, dc, checkBoard)
            }

            'k' -> {
                isLegalKingMove(piece, from, to, dr, dc, checkBoard)
            }

            else -> {
                false
            }
        }
    }

    private fun isLegalPawnMove(
        piece: Char,
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        dr: Int,
        dc: Int,
        checkBoard: CharArray,
        epTarget: Int,
    ): Boolean {
        val isWhite = piece == 'P'
        val direction = if (isWhite) -1 else 1
        val startRow = if (isWhite) 6 else 1

        return when {
            dr == direction && dc == 0 && checkBoard[toRow * 8 + toCol] == '.' -> {
                true
            }

            dr == 2 * direction && dc == 0 && fromRow == startRow &&
                checkBoard[toRow * 8 + toCol] == '.' &&
                checkBoard[(fromRow + direction) * 8 + fromCol] == '.' -> {
                true
            }

            // Capture or En Passant
            dr == direction && kotlin.math.abs(dc) == 1 -> {
                val toIdx = toRow * 8 + toCol
                checkBoard[toIdx] != '.' || toIdx == epTarget
            }

            else -> {
                false
            }
        }
    }

    private fun isLegalRookMove(
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        dr: Int,
        dc: Int,
        checkBoard: CharArray,
    ): Boolean {
        if (dr != 0 && dc != 0) return false
        return isPathClear(fromRow, fromCol, toRow, toCol, checkBoard)
    }

    private fun isLegalKnightMove(
        dr: Int,
        dc: Int,
    ): Boolean {
        val absDr = kotlin.math.abs(dr)
        val absDc = kotlin.math.abs(dc)
        return (absDr == 2 && absDc == 1) || (absDr == 1 && absDc == 2)
    }

    private fun isLegalBishopMove(
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        dr: Int,
        dc: Int,
        checkBoard: CharArray,
    ): Boolean {
        if (kotlin.math.abs(dr) != kotlin.math.abs(dc)) return false
        return isPathClear(fromRow, fromCol, toRow, toCol, checkBoard)
    }

    private fun isLegalKingMove(
        piece: Char,
        from: Int,
        to: Int,
        dr: Int,
        dc: Int,
        checkBoard: CharArray,
    ): Boolean {
        // Standard Move
        if (kotlin.math.abs(dr) <= 1 && kotlin.math.abs(dc) <= 1) return true

        // Castling
        val isWhite = isWhitePiece(piece)
        if (dr == 0 && kotlin.math.abs(dc) == 2) {
            // Cannot castle out of, through, or into check
            if (isAttacked(from, !isWhite, checkBoard)) return false

            if (isWhite && from == 60) {
                if (to == 62 && !whiteKingMoved && !whiteRookHMoved && checkBoard[61] == '.' && checkBoard[62] == '.') {
                    if (!isAttacked(61, !isWhite, checkBoard)) return true
                }
                if (to == 58 && !whiteKingMoved && !whiteRookAMoved && checkBoard[59] == '.' && checkBoard[58] == '.' &&
                    checkBoard[57] == '.'
                ) {
                    if (!isAttacked(59, !isWhite, checkBoard)) return true
                }
            } else if (!isWhite && from == 4) {
                if (to == 6 && !blackKingMoved && !blackRookHMoved && checkBoard[5] == '.' && checkBoard[6] == '.') {
                    if (!isAttacked(5, !isWhite, checkBoard)) return true
                }
                if (to == 2 && !blackKingMoved && !blackRookAMoved && checkBoard[3] == '.' && checkBoard[2] == '.' &&
                    checkBoard[1] == '.'
                ) {
                    if (!isAttacked(3, !isWhite, checkBoard)) return true
                }
            }
        }
        return false
    }

    private fun isPathClear(
        fromRow: Int,
        fromCol: Int,
        toRow: Int,
        toCol: Int,
        checkBoard: CharArray,
    ): Boolean {
        val stepRow = Integer.signum(toRow - fromRow)
        val stepCol = Integer.signum(toCol - fromCol)
        var r = fromRow + stepRow
        var c = fromCol + stepCol
        while (r != toRow || c != toCol) {
            if (checkBoard[r * 8 + c] != '.') return false
            r += stepRow
            c += stepCol
        }
        return true
    }

    private fun isWhitePiece(piece: Char): Boolean = piece.isUpperCase()

    fun currentPlayer(): Int = turn

    override fun isGameOver(): Boolean = winner != -2

    override fun getWinner(): Int = winner

    override fun getState(playerIndex: Int): Map<String, Any?> =
        mapOf(
            "board" to board.concatToString(),
            "turn" to turn,
            "gameOver" to isGameOver(),
            "winner" to if (isGameOver()) winner else null,
            "playerIndex" to playerIndex,
        )

    fun legalMovesFrom(from: Int): List<Int> = (0..63).filter { to -> isMoveLegalStrict(from, to) }

    private fun makeMoveDryRun(
        from: Int,
        to: Int,
        checkBoard: CharArray,
        epTarget: Int,
    ): Boolean {
        if (from !in 0..63 || to !in 0..63 || from == to) return false
        val piece = checkBoard[from]
        if (piece == '.') return false
        if (isWhitePiece(piece) != (turn == 0)) return false
        val target = checkBoard[to]
        if (target != '.' && isWhitePiece(target) == isWhitePiece(piece)) return false
        return isLegalMove(piece, from, to, checkBoard, epTarget)
    }

    // Build the state for networking
    // Needed in this class because it's unique to the game - could also put in handler
    override fun buildState(
        type: String,
        game: Game,
        playerIndex: Int,
    ): String {
        // askSuccess only required in GoFish
        val state = game.getState(playerIndex)
        return buildString {
            append("""{"type":"$type"""")
            append(""","board":"${state["board"]}"""")
            append(""","turn":${state["turn"]}""")
            append(""","gameOver":${state["gameOver"]}""")
            append(""","winner":${state["winner"] ?: "null"}""")
            append(""","playerIndex":$playerIndex}""")
        }
    }
}
