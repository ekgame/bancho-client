package lt.ekgame.bancho.client;

import java.util.List;

public interface CommandExecutor {
	
	public boolean accept(String channel, String sender);
	
	public void handle(String channel, String sender, int userId, String label, List<String> args);
	
}
