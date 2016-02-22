package lt.ekgame.bancho.client;

import java.util.ArrayList;
import java.util.List;

import lt.ekgame.bancho.api.packets.Packet;
import lt.ekgame.bancho.api.packets.server.PacketChat;

public class CommandHandler implements PacketHandler {
	
	private String commandPrefix;
	private List<CommandExecutor> commandExecutors = new ArrayList<>();
	
	public CommandHandler(String commandPrefix) {
		this.commandPrefix = commandPrefix;
	}
	
	public void addExecutor(CommandExecutor executor) {
		if (!commandExecutors.contains(executor))
			commandExecutors.add(executor);
	}

	@Override
	public void handle(Packet packet) {
		if (packet instanceof PacketChat) {
			PacketChat msg = (PacketChat) packet;
			if (!msg.channel.startsWith("#"))
				System.out.println(msg.sender + ": " + msg.message);
			
			if (msg.message.trim().startsWith(commandPrefix)) {
				String command = msg.message.trim().substring(1);
				String[] rawArgs = command.split(" ");
				if (rawArgs.length == 0)
					return;
				
				String label = rawArgs[0].toLowerCase();
				List<String> args = new ArrayList<>();
				for (int i = 1; i < rawArgs.length; i++)
					args.add(rawArgs[i]);
				handle(msg.channel, msg.sender, msg.userId, label, args);
			}
		}
	}
	
	private void handle(String channel, String sender, int userId, String label, List<String> args) {
		for (CommandExecutor executor : commandExecutors)
			if (executor.accept(channel, sender))
				executor.handle(channel, sender, userId, label, args);
	}
}
