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
    private var board: CharArray = CharArray(64) { '.' }

    /**
     * Player indices: 0 = White, 1 = Black.
     * White moves first (turn = 0).
     */
    private var turn: Int = 0

    /** Set to the winner index (0 or 1), -1 for draw, -2 while active. */
    private var winner: Int = -2 // -2 = game not over
    private var endReason: String = ""

    // Castling rights
    private var whiteKingMoved = false
    private var blackKingMoved = false
    private var whiteRookAMoved = false // a1 (56)
    private var whiteRookHMoved = false // h1 (63)
    private var blackRookAMoved = false // a8 (0)
    private var blackRookHMoved = false // h8 (7)

    // En passant target square (0-63), or -1 if none
    private var enPassantTarget = -1
    private var halfmoveClock = 0
    private val positionCounts = mutableMapOf<String, Int>()

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
        halfmoveClock = 0
        positionCounts.clear()
        turn = 0
        started = true
        winner = -2
        endReason = ""
        recordCurrentPosition()
    }

    // Move logic

    fun doCastle(
        theBoard: CharArray,
        piece: Char,
        to: Int,
        from: Int,
    ): CharArray {
        if (piece.lowercaseChar() == 'k' && kotlin.math.abs(from - to) == 2) {
            when (to) {
                62 -> {
                    // White Kingside
                    theBoard[61] = 'R'
                    theBoard[63] = '.'
                }

                58 -> {
                    // White Queenside
                    theBoard[59] = 'R'
                    theBoard[56] = '.'
                }

                6 -> {
                    // Black Kingside
                    theBoard[5] = 'r'
                    theBoard[7] = '.'
                }

                2 -> {
                    // Black Queenside
                    theBoard[3] = 'r'
                    theBoard[0] = '.'
                }
            }
        }

        return theBoard
    }

    /**
     * Chess move logic
     */
    fun makeMove(
        from: Int,
        to: Int,
        promotion: Char? = null,
    ): Boolean {
        if (isGameOver()) return false
        if (!isMovePossible(from, to)) return false

        if (!isMoveLegalStrict(from, to)) {
            return false
        }

        // Piece = board[from] to swap values around (basically to make the move)
        val target = board[to]
        val piece = board[from]
        if (isPromotionMove(piece, to) && !isValidPromotion(piece, promotion)) return false
        val isEnPassantCapture = piece.lowercaseChar() == 'p' && to == enPassantTarget && target == '.'

        // There's so much ####ing repeated code here. This must be AI slop.
        // Apply move
        board[to] = piece
        board[from] = '.'

        // Handle Castling Execution (King moved 2 squares)
        board = doCastle(board, piece, to, from)

        // Handle En Passant Execution
        if (isEnPassantCapture) {
            val captureIdx = if (isWhitePiece(piece)) to + 8 else to - 8
            board[captureIdx] = '.'
        }

        // Set En Passant Target for NEXT turn if pawn double step
        enPassantTarget = -1
        if (piece.lowercaseChar() == 'p' && kotlin.math.abs(from - to) == 16) {
            enPassantTarget = if (isWhitePiece(piece)) from - 8 else from + 8
        }

        // Pawn promotion (auto-promote to queen)
        // In other versions of chess, you can select what you promote to (only useful for knight really)
        if (isPromotionMove(piece, to)) board[to] = promotedPiece(piece, promotion)

        // Update castling rights
        if (piece == 'K') whiteKingMoved = true
        if (piece == 'k') blackKingMoved = true
        if (from == 56 || (target == 'R' && to == 56)) whiteRookAMoved = true
        if (from == 63 || (target == 'R' && to == 63)) whiteRookHMoved = true
        if (from == 0 || (target == 'r' && to == 0)) blackRookAMoved = true
        if (from == 7 || (target == 'r' && to == 7)) blackRookHMoved = true

        halfmoveClock = if (piece.lowercaseChar() == 'p' || target != '.' || isEnPassantCapture) 0 else halfmoveClock + 1

        // Advance turn
        turn = 1 - turn

        recordCurrentPosition()
        updateGameStatus()

        return true
    }

    private fun updateGameStatus() {
        if (hasInsufficientMaterial()) {
            winner = -1
            endReason = "insufficient_material"
            return
        }

        // After turn changes, check checkmate/stalemate.
        if (!hasLegalMoves(turn)) {
            val kingChar = if (turn == 0) 'K' else 'k'
            val kingPos = board.indexOfFirst { it == kingChar }
            val inCheck = if (kingPos != -1) isAttacked(kingPos, turn != 0, board) else true
            winner =
                if (inCheck) {
                    1 - turn
                } else {
                    -1
            }
            endReason = if (inCheck) "checkmate" else "stalemate"
            return
        }

        if (halfmoveClock >= 100) {
            winner = -1
            endReason = "fifty_move_rule"
            return
        }

        if ((positionCounts[positionKey()] ?: 0) >= 3) {
            winner = -1
            endReason = "threefold_repetition"
        }
    }

    private fun isPromotionMove(
        piece: Char,
        to: Int,
    ): Boolean = (piece == 'P' && to < 8) || (piece == 'p' && to >= 56)

    private fun isValidPromotion(
        piece: Char,
        promotion: Char?,
    ): Boolean {
        if (promotion == null) return true
        val chosen = promotion ?: 'q'
        return chosen.lowercaseChar() in setOf('q', 'r', 'b', 'n') && isWhitePiece(chosen) == isWhitePiece(piece)
    }

    private fun promotedPiece(
        piece: Char,
        promotion: Char?,
    ): Char {
        val chosen = promotion?.lowercaseChar() ?: 'q'
        return if (isWhitePiece(piece)) chosen.uppercaseChar() else chosen
    }

    private fun recordCurrentPosition(): Int {
        val key = positionKey()
        val count = (positionCounts[key] ?: 0) + 1
        positionCounts[key] = count
        return count
    }

    private fun positionKey(): String =
        listOf(
            board.concatToString(),
            turn.toString(),
            castlingRightsKey(),
            enPassantTarget.toString(),
        ).joinToString("|")

    private fun castlingRightsKey(): String =
        buildString {
            if (!whiteKingMoved && !whiteRookHMoved && board[63] == 'R') append('K')
            if (!whiteKingMoved && !whiteRookAMoved && board[56] == 'R') append('Q')
            if (!blackKingMoved && !blackRookHMoved && board[7] == 'r') append('k')
            if (!blackKingMoved && !blackRookAMoved && board[0] == 'r') append('q')
            if (isEmpty()) append('-')
        }

    private fun hasInsufficientMaterial(): Boolean {
        val pieces = board.withIndex().filter { it.value != '.' }
        val nonKings = pieces.filter { it.value.lowercaseChar() != 'k' }

        if (nonKings.any { it.value.lowercaseChar() in setOf('p', 'r', 'q') }) return false
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1 && nonKings.first().value.lowercaseChar() in setOf('b', 'n')) return true

        val allBishops = nonKings.all { it.value.lowercaseChar() == 'b' }
        if (allBishops) {
            val bishopSquareColors = nonKings.map { (it.index / 8 + it.index % 8) % 2 }
            return bishopSquareColors.distinct().size == 1
        }

        return false
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
        doCastle(tempBoard, piece, to, from)

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

    /**
     * Utility function returning row/col of to/from pieces
     */
    private fun getRowCol(i: Int): Pair<Int, Int> = i / 8 to i % 8

    private fun isLegalMove(
        piece: Char,
        from: Int,
        to: Int,
        checkBoard: CharArray,
        epTarget: Int,
    ): Boolean {
        val (fromRow, fromCol) = getRowCol(from)
        val (toRow, toCol) = getRowCol(to)

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

        return when (dr) {
            direction if dc == 0 && checkBoard[toRow * 8 + toCol] == '.' -> {
                true
            }

            2 * direction if dc == 0 && fromRow == startRow &&
                checkBoard[toRow * 8 + toCol] == '.' &&
                checkBoard[(fromRow + direction) * 8 + fromCol] == '.' -> {
                true
            }

            // Capture or En Passant
            direction if kotlin.math.abs(dc) == 1 -> {
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
                if (to == 62 && !whiteKingMoved && !whiteRookHMoved && checkBoard[63] == 'R' &&
                    checkBoard[61] == '.' && checkBoard[62] == '.'
                ) {
                    if (!isAttacked(61, false, checkBoard)) return true
                }
                if (to == 58 && !whiteKingMoved && !whiteRookAMoved && checkBoard[56] == 'R' &&
                    checkBoard[59] == '.' && checkBoard[58] == '.' &&
                    checkBoard[57] == '.'
                ) {
                    if (!isAttacked(59, false, checkBoard)) return true
                }
            } else if (!isWhite && from == 4) {
                if (to == 6 && !blackKingMoved && !blackRookHMoved && checkBoard[7] == 'r' &&
                    checkBoard[5] == '.' && checkBoard[6] == '.'
                ) {
                    if (!isAttacked(5, true, checkBoard)) return true
                }
                if (to == 2 && !blackKingMoved && !blackRookAMoved && checkBoard[0] == 'r' &&
                    checkBoard[3] == '.' && checkBoard[2] == '.' &&
                    checkBoard[1] == '.'
                ) {
                    if (!isAttacked(3, true, checkBoard)) return true
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

    fun getEndReason(): String = endReason

    override fun getState(playerIndex: Int): Map<String, Any?> =
        mapOf(
            "board" to board.concatToString(),
            "turn" to turn,
            "gameOver" to isGameOver(),
            "winner" to if (isGameOver()) winner else null,
            "endReason" to endReason,
            "playerIndex" to playerIndex,
        )

    fun legalMovesFrom(from: Int): List<Int> = (0..63).filter { to -> isMoveLegalStrict(from, to) }

    /**
     * Checks if the given move is actually a possible move
     */
    fun isMovePossible(
        from: Int,
        to: Int,
    ): Boolean {
        if (from !in 0..63 || to !in 0..63 || from == to) return false
        val piece = board[from]
        if (piece == '.') return false
        if (isWhitePiece(piece) != (turn == 0)) return false

        val target = board[to]
        if (target.lowercaseChar() == 'k') return false
        return !(target != '.' && isWhitePiece(target) == isWhitePiece(piece))

        // Otherwise, the move should be possible
    }

    private fun makeMoveDryRun(
        from: Int,
        to: Int,
        checkBoard: CharArray,
        epTarget: Int,
    ): Boolean {
        if (!isMovePossible(from, to)) return false

        val piece = board[from]
        return isLegalMove(piece, from, to, checkBoard, epTarget)
    }

    /**
     * Build chess state for networking
     */
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
            append(",\"endReason\":\"${state["endReason"]}\"")
            append(""","playerIndex":$playerIndex}""")
        }
    }
}
