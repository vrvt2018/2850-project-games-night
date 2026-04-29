package com.example.games

/**
 * Abstract base class representing a generic game.
 *
 * @param name The display name of the game.
 * @param maxPlayers The maximum number of players allowed in this game.
 */
abstract class Game(val name: String, val maxPlayers: Int = 2, val minPlayers: Int = 2) {

    /** Current number of active players in this game instance. */
    var numPlayers: Int = 0
        protected set

    /** Whether the game has been started. */
    var started: Boolean = false
        protected set

    /** Adds a player to the game. Returns false if the game is full. */
    abstract fun addPlayer(): Boolean

    /** Starts the game (deals cards, sets initial state, etc.). */
    abstract fun startGame()

    /** Returns true when the game has ended. */
    abstract fun isGameOver(): Boolean

    /** Returns the index of the winning player, or -1 for a draw. */
    abstract fun getWinner(): Int

    /**
     * Returns a map of the current game state suitable for JSON serialisation.
     * @param playerIndex The player requesting the state (used to hide opponent cards etc.)
     */
    abstract fun getState(playerIndex: Int = -1): Map<String, Any?>

    // Currently in here for use with networking when porting. Should probably be moved
    abstract fun buildState(type: String, game: Game, playerIndex: Int): String
}
