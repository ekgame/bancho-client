# Bancho client
A framework for advanced osu!Bancho bots. This framework has the advantage over standard IRC bots as it enables you to create, join and control multiplayer rooms, spectate players, etc.

The framework currentry features authentication, handling some of the more important packets, keeping a connection alive and a basic interaction with multiplayer. It is not feature complete and there are still plenty of things that need to be worked on.

# Disclamer
To run a bot, you must have a valid osu! account. You can **not** run a bot and play on the same account at the same time. To run a bot, you will need to either borrow or create a new account exclusively for the bot. If you do decide to **create a new account**, be careful - **do not play on the account.** osu! has very strict rules for multi-accounting.

I also discourage you from sending messages to any of the public chat channels (such as #osu). Context-based channels, such as #multiplayer and #spectate, should be fine.

As many of these kind of projects, I will take this down if peppy requests me to.

# Depencencies
Most of the dependency management is done with Maven. There is only one library that you will need to reference manually:
* [Bancho API](https://github.com/ekgame/bancho-api) - the commons API used for packet parsing.


# Usage
Basic client example:
```Java
// Create and instance with the username and password that the bot will use
BanchoClient bancho = new BanchoClient("username", "password", false, false);

// Register a handeler to take action on some kind of an event
bancho.registerHandler((Packet packet) -> {
  if (packet instanceof PacketReceivingFinished) {
		bancho.sendMessage("ekgame", "Hello there, pal!"); // send a message to a user
		System.out.println("Welcome to osu!Bancho!");
	}
});

// Authenticate the account (sync)
bancho.connect();

// Start the client (async)
bancho.start();
```

Creating and managing a multiplayer room:
```Java
// Get the multiplayer helper instance
MultiplayerHandler mp = bancho.getMultiplayerHandler();

// Signal Bancho that you want to use multiplayer
mp.enableMultiplayer();

// Create a basic room with a name, password and the number of open slots.
mp.createRoom("Room name", "password", 16);

// ...or create a room without a password
mp.createRoom("Room name", null, 16);

// Setting a beatmap
Beatmap beatmap = new Beatmap(artist, title, version, creator, beatmapMD5, beatmapId);
mp.setBeatmap(beatmap);

// Changing the room name
mp.setRoomName("TeamVS: blue 1:0 red");

// Starting the game (no need to be in a *ready* state)
mp.startGame();

// Send a message to #multiplayer channel
bancho.sendMessage("#multiplayer", "Hello world!");
```
