package lt.ekgame.bancho.client;

import lt.ekgame.bancho.api.packets.Packet;

public interface PacketHandler {
	
	public void handle(Packet packet);

}
