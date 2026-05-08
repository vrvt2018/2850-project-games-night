# Games night

This is the Games Night application!

After creating an account, you can choose any game available and play with your friends by simply sharing the code with them! They can also see your room code in the list of active rooms.

This application currently supports Chess and Go Fish.

# Adding games
To add games to application, you need:
- A pebble file containing your front-end code which inherits from `game.peb`.
- A javascript file containing
- Two kotlin files
  - A back-end game class inheriting abstract class `Game`
  - A network handler inheriting abstract class `GameSocketHandler`

Finally, you need to add your game to the list of games in the database, and your game should be playable!
