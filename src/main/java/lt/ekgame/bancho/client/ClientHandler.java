package lt.ekgame.bancho.client;

import lt.ekgame.bancho.api.packets.Packet;
import lt.ekgame.bancho.api.packets.client.PacketStatusUpdate;
import lt.ekgame.bancho.api.packets.server.PacketProtocolVersion;
import lt.ekgame.bancho.api.packets.server.PacketReceivingFinished;
import lt.ekgame.bancho.api.packets.server.PacketUserId;
import lt.ekgame.bancho.api.units.Beatmap;
import lt.ekgame.bancho.api.units.Mods;
import lt.ekgame.bancho.api.units.PlayMode;
import lt.ekgame.bancho.api.units.UserStatus;

public class ClientHandler implements PacketHandler {
	
	private BanchoClient bancho;
	
	private Beatmap currentBeatmap = Beatmap.DEFAULT;
	private UserStatus currentStatus = UserStatus.IDLE;
	private Mods currentMods = new Mods(0);
	private PlayMode currentPlaymode = PlayMode.OSU;
	
	private int protocolVersion = -1;
	private int userId = -1;
	private boolean isConnected = false;
	
	public ClientHandler(BanchoClient bancho) {
		this.bancho = bancho;
	}
	
	@Override
	public void handle(Packet packet) {
		if (packet instanceof PacketProtocolVersion)
			protocolVersion = ((PacketProtocolVersion) packet).protocolVersion;
		
		if (packet instanceof PacketUserId)
			userId = ((PacketUserId) packet).userId;
		
		if (packet instanceof PacketReceivingFinished) {
			sendStatusUpdate();
			isConnected = true;
		}
	}
	
	public void setCurrentBeatmap(Beatmap beatmap) {
		currentBeatmap = beatmap;
	}
	
	public void setPlaymode(PlayMode playmode) {
		currentPlaymode = playmode;
	}
	
	public void setMods(Mods mods) {
		currentMods = mods;
	}
	
	public void setStatus(UserStatus status) {
		currentStatus = status;
	}
	
	public void sendStatusUpdate() {
		bancho.sendPacket(new PacketStatusUpdate(currentStatus, currentBeatmap, currentMods, currentPlaymode));
	}

	public int getProtocolVersion() {
		return protocolVersion;
	}
	
	public int getUserId() {
		return userId;
	}

	public Beatmap getCurrentBeatmap() {
		return currentBeatmap;
	}

	public Mods getMods() {
		return currentMods;
	}
	
	public boolean isConnected() {
		return isConnected;
	}
	
}
