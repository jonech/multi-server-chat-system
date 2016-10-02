package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Server State stores ChatServer and Main-Halls
 */
public class ServerState {

	private static ServerState instance;

	// cache up all the ChatServer
	private HashMap<String, ChatServer> serverObjectMap;
	// all the coordination port of the ChatServer
	private HashMap<String, Integer> serverPortMap;
	// all the server address for the Chat Server
	private HashMap<String, String> serverAddrMap;
	// all the Main-Hall
	private List<ChatRoom> globalRoomList;

	private ServerState() {
		globalRoomList = new ArrayList<>();
		serverPortMap = new HashMap<>();
		serverObjectMap = new HashMap<>();
		serverAddrMap = new HashMap<>();
	}

	public static synchronized ServerState getInstance() {
		if(instance == null) {
			instance = new ServerState();
		}
		return instance;
	}

	/* get a list of Main-Hall */
	public synchronized List<ChatRoom> getGlobalRooms() { return globalRoomList; }

	/* get the HashMap for server port */
	public synchronized HashMap<String, Integer> getServerPortMap() {
		return serverPortMap;
	}

	/* get the HashMap for server address */
	public synchronized HashMap<String, String> getServerAddrMap() {
		return serverAddrMap;
	}

	/* get the ChatServer object */
	public synchronized ChatServer getServerObject(String serverID) { return serverObjectMap.get(serverID); }

	/* get ALL ChatServer object */
	public synchronized List<ChatServer> getAllServerObject() {
		List<ChatServer> allServers = new ArrayList<>();
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
	public synchronized void serverConnected(String serverID, ChatServer server, String address, int port) {
		System.out.println("cached: " + serverID + " "+port);
		serverPortMap.put(serverID, port);
		serverAddrMap.put(serverID, address);
		serverObjectMap.put(serverID, server);
	}

	/* get ALL Local Chat Room and Main-Hall */
	public synchronized List<ChatRoom> getAllGlobalLocalChatRoom()
	{
		List<ChatRoom> allRooms = new ArrayList<>();

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

}
