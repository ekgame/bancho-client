package lt.ekgame.bancho.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TimeZone;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClients;

import lt.ekgame.bancho.api.Bancho;
import lt.ekgame.bancho.api.exceptions.LoginException;
import lt.ekgame.bancho.api.exceptions.StateException;
import lt.ekgame.bancho.api.packets.ByteDataInputStream;
import lt.ekgame.bancho.api.packets.ByteDataOutputStream;
import lt.ekgame.bancho.api.packets.Packet;
import lt.ekgame.bancho.api.packets.Packets;
import lt.ekgame.bancho.api.packets.client.*;
import lt.ekgame.bancho.api.packets.server.*;

public class BanchoClient extends Thread implements Bancho {
	
	public static final String SCHEME_DEFAULT = "http";
	public static final String SCHEME_SECURE = "https";
	public static final String OSU_BANCHO_CONNECT = "/web/bancho_connect.php";
	
	public static final String OSU_HOST = "osu.ppy.sh";
	public static final String BANCHO_HOST = "c.ppy.sh";
	
	public static final String CLIENT_VERSION = "b20160403.6";
	public static final String EXE_HASH = "35f34a2110e715864cea6ebb5d7d7df8";
	
	public String username;
	public String password;
	public String countryCode;
	private String token = null;
	private HttpClient httpClient;
	private boolean isSecure = false;
	private boolean verbose = false;
	
	public static URI BANCHO_URI;
	
	private ClientHandler clientHandler;
	private MultiplayerHandler multiplayerHandler;
	private CommandHandler commandHandler;
	
	private Queue<Packet> outgoingPackets = new LinkedList<>();
	private List<PacketHandler> packetHandlers = new ArrayList<PacketHandler>();
	
	private boolean isConnected = false;
	
	public BanchoClient(String username, String password, boolean secure, boolean verbose) throws URISyntaxException {
		this.username = username;
		this.password = DigestUtils.md5Hex(password);
		this.isSecure = secure;
		this.verbose = verbose;
		
		BANCHO_URI = new URIBuilder()
				.setScheme(isSecure ? SCHEME_SECURE : SCHEME_DEFAULT)
				.setHost(BANCHO_HOST)
				.setPath("/")
				.build();
		
		clientHandler = new ClientHandler(this);
		multiplayerHandler = new MultiplayerHandler(this, clientHandler);
		commandHandler = new CommandHandler("!");
		
		registerHandler(clientHandler);
		registerHandler(multiplayerHandler);
		registerHandler(commandHandler);
	}
	
	public String getCountryCode() {
		return countryCode;
	}
	
	public void connect() throws URISyntaxException, ClientProtocolException, IOException, LoginException {
		
		// 10 second timeout
		RequestConfig defaultRequestConfig = RequestConfig.custom()
			    .setSocketTimeout(10000)
			    .setConnectTimeout(10000)
			    .setConnectionRequestTimeout(10000)
			    .build();
		
		httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build();
		URI uri = new URIBuilder()
				.setScheme(isSecure ? SCHEME_SECURE : SCHEME_DEFAULT)
				.setHost(OSU_HOST)
				.setPath(OSU_BANCHO_CONNECT)
				.setParameter("v", CLIENT_VERSION)
				.setParameter("u", username)
				.setParameter("h", password)
				.build();
		HttpGet request = new HttpGet(uri);
		HttpResponse response = httpClient.execute(request);
		InputStream content = response.getEntity().getContent();
		String stringContent = IOUtils.toString(content, "UTF-8"); 
		if (stringContent.isEmpty()) {
			httpClient = null; // invalidate http client
			throw new LoginException("Failed to login (invalid creditials?)");
		}
		countryCode = stringContent;
		authentifyBancho();
	}
	
	public void registerHandler(PacketHandler handler) {
		if (!packetHandlers.contains(handler))
			packetHandlers.add(handler);
	}
	
	public void removeHandler(PacketHandler handler) {
		if (packetHandlers.contains(handler))
			packetHandlers.remove(handler);
	}
	
	private void authentifyBancho() throws URISyntaxException, ClientProtocolException, IOException, LoginException {
		if (httpClient == null)
			throw new StateException("Invalid HTTP client.");
		
		HttpClientContext httpContext = new HttpClientContext();
		httpContext.setCookieStore(new BasicCookieStore());
		
		String requestBody = username + "\n" + password + "\n";
		// Documentation for the third line:
		// (osu client version)|(UTC offset)|(1 if your city should be public, 0 otherwise)|(MD5 hashed described below)|(1 if non-friend PMs should be blocked, 0 otherwise)
		// 4th argument: <MD5 hash for the executable>::<MD5 for empty string>:<MD5 for "unknown">:<MD5 for "unknown">
		// Only the first one seems to really matter.
		// Latest MD5 hash for the osu!.exe executable can be found here: https://goo.gl/IVUVA3
		TimeZone tz = TimeZone.getDefault();
		int offset = tz.getRawOffset()/3600000;
		requestBody += CLIENT_VERSION + "|" + offset + "|0|" + EXE_HASH + "::d41d8cd98f00b204e9800998ecf8427e:ad921d60486366258809553a3db49a4a:ad921d60486366258809553a3db49a4a:|0" + "\n";
		HttpEntity entity = new ByteArrayEntity(requestBody.getBytes("UTF-8"));
		
		HttpPost request = new HttpPost(BANCHO_URI);
		request.setEntity(entity);
		request.addHeader("osu-version", CLIENT_VERSION);
		request.addHeader("Accept-Encoding", "gzip");
		request.addHeader("User-Agent", "osu!");
		request.addHeader("Connection", "Keep-Alive");
		
		HttpResponse response = httpClient.execute(request, httpContext);
		
		for (Header header : response.getAllHeaders()) {
			if (header.getName().equals("cho-token")) {
				token = header.getValue();
			}
		}

		if (response.getStatusLine().getStatusCode() != 200) {
			httpClient = null; // invalidate http client
			throw new LoginException("Failed to authentify to bancho (invalid creditials? offline?)");
		}
		
		InputStream content = response.getEntity().getContent();
		handleBanchoResponse(content);
	}
	
	private void handleBanchoResponse(InputStream content) throws IOException {
		ByteDataInputStream in = new ByteDataInputStream(content, this);
		
		try {
			while (true) {
				int type = in.readShort();
				in.skipBytes(1);
				int len = in.readInt();
				if (len>9999999) {
					System.out.println("Packet reading error. len: " + len);
					break; // something went horribly wrong, abort.
				}
				byte[] bytes = new byte[len];
				for (int i = 0; i < len; i++)
					bytes[i] = in.readByte();
				
				Class<? extends Packet> packetClass = Packets.getById(type);
				if (packetClass != null) {
					try {
						Packet packet = (Packet) packetClass.newInstance();
						if (verbose && !(packet instanceof PacketIdle) && !(packet instanceof PacketUnknown0B) && !(packet instanceof PacketUnknown5F) && !(packet instanceof PacketUnknown0C)
						 && !(packet instanceof PacketRoomUpdate) && !(packet instanceof PacketUnknown1B)&& !(packet instanceof PacketUnknown1C))
							System.out.printf(" in >>  %s\n", packet.getClass().getName());
						ByteDataInputStream stream = new ByteDataInputStream(new ByteArrayInputStream(bytes), this);
						packet.read(stream, len);
						handlePacket(packet);
					} catch (InstantiationException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				} else {
					if (verbose) {
						System.out.printf("Unknown packet: %02X %08X \n- Data: ", type, len);
						for (int i = 0; i < len; i++)
							System.out.printf("%02x ", bytes[i]);
						System.out.println();
					}
				}
			}
		} catch (EOFException e) {
			// Finished
		}
	}
	
	private void handlePacket(Packet packet) {
		if (packet instanceof PacketReceivingFinished) {
			isConnected = true;
		}
		
		for (PacketHandler handler : packetHandlers)
			handler.handle(packet);
	}

	long lastRequest = 0;
	
	public void run() {
		while (true) {
			//System.out.println("Update");
			lastRequest = System.currentTimeMillis();
			if (outgoingPackets.isEmpty())
				outgoingPackets.add(new PacketIdle());
			
			HttpClientContext httpContext = new HttpClientContext();
			httpContext.setCookieStore(new BasicCookieStore());
			
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ByteDataOutputStream stream = new ByteDataOutputStream(out, this);
			
			while (!outgoingPackets.isEmpty()) {
				Packet packet = outgoingPackets.poll();
				short id = (short) Packets.getId(packet);
				if (id == -1) {
					System.err.println("Can't find ID for " + packet.getClass());
					continue;
				}
				if (verbose && !(packet instanceof PacketIdle))
					System.out.printf("out >>  %s\n", packet.getClass().getName());
				try {
					stream.writeShort(id);
					stream.writeByte((byte) 0);
					stream.writeInt(packet.size(this));
					packet.write(stream);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			
			HttpEntity entity = new ByteArrayEntity(out.toByteArray());
			
			HttpPost request = new HttpPost(BANCHO_URI);
			request.setEntity(entity);
			request.addHeader("osu-token", token);
			request.addHeader("Accept-Encoding", "gzip");
			request.addHeader("User-Agent", "osu!");
			request.addHeader("Connection", "Keep-Alive");
			
			try {
				// XXX Packets will be lost if this times out
				HttpResponse response = httpClient.execute(request, httpContext);
				handleBanchoResponse(response.getEntity().getContent());
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				request.releaseConnection();
			}
			
			// Wait atleast 0.5 second between requests
			while (System.currentTimeMillis() - lastRequest < 500) {}
		}
	}
	
	public void sendPacket(Packet packet) {
		outgoingPackets.add(packet);
	}
	
	public void sendMessage(String channel, String message) {
		if (channel.startsWith("#"))
			sendPacket(new PacketSendMessageChannel(message, channel));
		else
			sendPacket(new PacketSendMessageUser(message, channel));
	}
	
	public void beginSpectating(int userId) {
		sendPacket(new PacketStartSpectating(userId));
	}
	
	public void endSpectating() {
		sendPacket(new PacketStopSpectating());
	}

	public int getProtocolVersion() {
		return clientHandler.getProtocolVersion();
	}
	
	public ClientHandler getClientHandler() {
		return clientHandler;
	}
	
	public MultiplayerHandler getMultiplayerHandler() {
		return multiplayerHandler;
	}
	
	public CommandHandler getCommandHandler() {
		return commandHandler;
	}
	
	public boolean isConnected() {
		return isConnected;
	}
}
