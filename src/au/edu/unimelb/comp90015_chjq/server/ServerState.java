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

	// cache up all the ChatServer
	private HashMap<String, ChatServer> serverObjectMap;
	// all the coordination port of the ChatServer
	//private HashMap<String, Integer> serverPortMap;
	// all the server address for the Chat Server
	//private HashMap<String, String> serverAddrMap;
	
	// for server's information
	private HashMap<String, ChatServerInfo> serverInfoMap;
	
	// all the Main-Hall
	private List<ChatRoom> globalRoomList;
	private ServerState() {
		globalRoomList = new ArrayList<ChatRoom>();
		serverObjectMap = new HashMap<String, ChatServer>();
		serverInfoMap = new HashMap<String, ChatServerInfo>();
	}

	public static synchronized ServerState getInstance() {
		if(instance == null) {
			instance = new ServerState();
		}
		return instance;
	}

	public synchronized  void removeRemoteServer(String serverID) {
		serverInfoMap.remove(serverID);
		System.out.println("Remove remote server " + serverID);
		Iterator<ChatRoom> it = globalRoomList.iterator();
		while (it.hasNext()) {
			ChatRoom room = it.next();
			//System.out.println(room.server + " " + serverID + " " + room.server.equals(serverID));
			if (room.server.equals(serverID)) {
				it.remove();
				System.out.println("remove global room " + room.getRoomName());
			}
		}
	}
	/* get a list of Main-Hall */
	public synchronized List<ChatRoom> getGlobalRooms() { return globalRoomList; }
	
	/* get HashMap for server info */
	public synchronized HashMap<String, ChatServerInfo> getServerInfoMap() { return serverInfoMap; }
	
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

	/* create a Main-Hall, called when ChatServer is created */
	public synchronized void createGlobalChatRoom(String server, String roomName, String owner) {
		ChatRoom room = new ChatRoom(server, roomName, owner);
		globalRoomList.add(room);
	}

	/* cache up the ChatServer details (coordination port, address, id) */
	public synchronized void addLocalServer(String serverID, ChatServer server, String address, int port) {
		System.out.println("cached: " + serverID + " "+port);
		
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
		List<ChatRoom> allRooms = new ArrayList<ChatRoom>();

		for (ChatRoom globalRoom : getGlobalRooms()) {
			allRooms.add(globalRoom);
		}
		for (ChatServer server : getAllServerObject()) {
			for (ChatRoom localRoom : server.getLocalRoomList()) {
				allRooms.add(localRoom);
			}
		}
		return allRooms;
	}

	/* find the ChatRoom object from all ChatServer, including the Main-Hall */
	public synchronized ChatRoom getChatRoomFromAll(String roomName)
	{
		for (ChatRoom globalRoom : getGlobalRooms()) {
			if (globalRoom.getRoomName().matches(roomName)) {
				return globalRoom;
			}
		}

		for (ChatServer server : getAllServerObject()) {
			for (ChatRoom localRoom : server.getLocalRoomList()) {
				if (localRoom.getRoomName().matches(roomName)) {
					return localRoom;
				}
			}
		}

		return null;
	}

	/* client joins into the Main-Hall */
	public synchronized ChatRoom joinGlobalChatRoom(String roomName, ClientConnection client) {

		for (ChatRoom room : globalRoomList) {
			if (room.getRoomName().matches(roomName)) {
				room.clientJoin(client);
				return room;
			}
		}
		return null;
	}

	/* get the Main-Hall object */
	public synchronized ChatRoom getServerChatRoom(String server) {
		for (ChatRoom room : globalRoomList) {
			if (room.getRoomName().matches((String)"MainHall-" + server)) {
				return room;
			}
		}
		return null;
	}


	/**
	 *  To Avoid ConcurrentModificationException when rooms get deleted
	 *  Every clients forced to change room to MainHall
	 *  Need to only allow one client change room at a time
	 *  So that the MessageQueue will not get modified concurrently
	 * */
	public synchronized void safeChangeRoom(ChatRoom former, ChatRoom newRoom, ClientConnection clientConnection)
	{
		JSONObject leaverMsg = new JSONObject();
		leaverMsg.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		leaverMsg.put(JSONTag.IDENTITY, clientConnection.clientID);
		leaverMsg.put(JSONTag.FORMER, former.getRoomName());
		leaverMsg.put(JSONTag.ROOMID, newRoom.getRoomName());

		if (!former.getRoomName().matches(newRoom.getRoomName())) {

			// broadcast to the client in the old room
			for (ClientConnection oldRoomClient : former.getConnectedClients()) {
				oldRoomClient.getMessageQueue().add(new Message(false, leaverMsg.toJSONString()));
			}
			// remove client from the old chat room
			former.clientLeave(clientConnection);

			// broadcast to client in new room
			for (ClientConnection newRoomClient : newRoom.getConnectedClients()) {
				newRoomClient.getMessageQueue().add(new Message(false, leaverMsg.toJSONString()));
			}
			// put client into the new chat room
			newRoom.clientJoin(clientConnection);
			// also cache the chatroom object in clientconnection
			clientConnection.currentRoom = newRoom;
			System.out.println(clientConnection.clientID + " join " + newRoom.getRoomName());
		}
		else {
			// join fail, so former room is the same as new room
			clientConnection.getMessageQueue().add(new Message(false, leaverMsg.toJSONString()));
		}
	}
	
	/**
	 * Remove the given server from all server's mapping
	 * @param serverID
	 */
	public synchronized void removeServer(String serverID)
	{
		serverObjectMap.remove(serverID);
		
		// find the MainHall of the server and remove
		Iterator<ChatRoom> it = globalRoomList.iterator();
		while (it.hasNext()) {
			ChatRoom room = it.next();
			if (room.server.equals(serverID)) {
				it.remove();
				System.out.println("Remove room " + room.getRoomName());
			}
		}
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
			if (entry.getKey().matches(serverID) || entry.getValue().isLocal)
				continue;
			
			String requestServerID = entry.getKey();
			int requestPort = Integer.parseInt(entry.getValue().port);
			String requestAddress = entry.getValue().address;
			
			ShortSender send = new ShortSender(requestServerID, requestAddress, requestPort, broadcast);
			new Thread(send).start();
		}
		
	}
	
}
