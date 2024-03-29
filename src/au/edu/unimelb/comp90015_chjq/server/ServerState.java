package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;

/**
 * Server State stores ChatServer and Main-Halls
 */
public class ServerState {

	private static ServerState instance;

	public static String authServerAddr;
	public static int authServerPort;

	// cache up all the ChatServer
	private HashMap<String, ChatServer> serverObjectMap;
	
	// for server's information
	private HashMap<String, ChatServerInfo> serverInfoMap;
	


	private ServerState() {
		//globalRoomList = new ArrayList<>();
		serverObjectMap = new HashMap<>();
		serverInfoMap = new HashMap<>();
	}

	public static synchronized ServerState getInstance() {
		if(instance == null) {
			instance = new ServerState();
		}
		return instance;
	}


	public synchronized  void removeRemoteServer(String serverID)
	{
		serverInfoMap.remove(serverID);
		System.out.println("Remove remote server " + serverID);
	}

	
	/* get HashMap for server info */
	public synchronized HashMap<String, ChatServerInfo> getServerInfoMap() { return serverInfoMap; }
	
	/* get List of remote server info */
	public synchronized List<ChatServerInfo> getRemoteServerInfo()
	{
		List<ChatServerInfo> remotes = new ArrayList<>();
		for (HashMap.Entry<String, ChatServerInfo> entry : ServerState.getInstance().getServerInfoMap().entrySet()) {
			
			if (entry.getValue().isLocal) {
				continue;
			}
			remotes.add(entry.getValue());
		}
		return remotes;
	}
	
	/* get the ChatServer object */
	public synchronized ChatServer getServerObject(String serverID) { return serverObjectMap.get(serverID); }
	
	/* check if server id existed */
	public synchronized boolean hasServer(String serverID) { return serverObjectMap.containsKey(serverID); }
	
	/* get ALL ChatServer object */
	public synchronized List<ChatServer> getAllServerObject() {
		List<ChatServer> allServers = new ArrayList<ChatServer>();
		for (HashMap.Entry<String, ChatServer> severObject : serverObjectMap.entrySet()) {
			allServers.add(severObject.getValue());
		}
		return allServers;
	}


	/* cache up the ChatServer details (coordination port, address, id) */
	public synchronized void addLocalServer(String serverID, ChatServer server, String address, int port) {
		
		serverInfoMap.put(serverID, new ChatServerInfo(serverID, address, Integer.toString(port), true));
		serverObjectMap.put(serverID, server);
	}
	
	public synchronized void addRemoteServer(String serverID, String address, int port)
	{
		// only add if the remote server does not exist
		if (!serverInfoMap.containsKey(serverID)) {
			System.out.println(serverID + " is added!");
			serverInfoMap.put(serverID, new ChatServerInfo(serverID, address, Integer.toString(port), false));
		}
			
	}
	
	/* get ALL Local Chat Room and Main-Hall */
	public synchronized List<ChatRoom> getAllGlobalLocalChatRoom()
	{
		List<ChatRoom> allRooms = new ArrayList<>();
		
		for (ChatServer server : getAllServerObject()) {
			// main hall
			allRooms.add(server.getServerMainHall());
			// local room
			for (ChatRoom localRoom : server.getLocalRoomList()) {
				allRooms.add(localRoom);
			}
		}
		return allRooms;
	}

	/* find the ChatRoom object from all ChatServer, including the Main-Hall */
	public synchronized ChatRoom findChatRoomFromAll(String roomName)
	{
		for (ChatServer server : getAllServerObject()) {
			
			if (server.getServerMainHall().getRoomName().equals(roomName)) {
				return server.getServerMainHall();
			}
			for (ChatRoom localRoom : server.getLocalRoomList()) {
				if (localRoom.getRoomName().matches(roomName)) {
					return localRoom;
				}
			}
		}
		return null;
	}
	
	/**
	 * Remove the given server from all server's mapping
	 * @param serverID
	 */
	public synchronized void removeServer(String serverID)
	{
		serverObjectMap.remove(serverID);
		
	}
	
	/**
	 * Broadcast a short connection message to all server
	 * @param serverID Server that creates the broadcast
	 * @param broadcast message
	 */
	public synchronized void broadcastShortRemoteServer(String serverID, String broadcast)
	{
		// broadcast to all servers
		for (HashMap.Entry<String, ChatServerInfo> entry : ServerState.getInstance().getServerInfoMap().entrySet()) {
			// skip if it loops to the ChatServer itself or local servers
			if (entry.getKey().matches(serverID) || entry.getValue().isLocal || entry.getKey().equals("auth"))
				continue;
			
			String requestServerID = entry.getKey();
			int requestPort = Integer.parseInt(entry.getValue().port);
			String requestAddress = entry.getValue().address;
			
			ShortSender send = new ShortSender(requestServerID, requestAddress, requestPort, broadcast);
			new Thread(send).start();
		}
		
	}
	
}
