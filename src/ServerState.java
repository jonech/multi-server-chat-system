
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jonech on 8/09/2016.
 */
public class ServerState {

	private static ServerState instance;

	private HashMap<String, ChatServer> serverObjectMap;
	private HashMap<String, Integer> serverPortMap;
	private HashMap<String, String> serverAddrMap;
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


	public synchronized List<ChatRoom> getGlobalRooms() { return globalRoomList; }

	public synchronized HashMap<String, Integer> getServerPortMap() {
		return serverPortMap;
	}

	public synchronized HashMap<String, String> getServerAddrMap() {
		return serverAddrMap;
	}

	public synchronized ChatServer getServerObject(String serverID) { return serverObjectMap.get(serverID); }

	public synchronized List<ChatServer> getAllServerObject() {
		List<ChatServer> allServers = new ArrayList<>();
		for (HashMap.Entry<String, ChatServer> severObject : serverObjectMap.entrySet()) {
			allServers.add(severObject.getValue());
		}
		return allServers;
	}

	public synchronized void createGlobalChatRoom(String server, String roomName, String owner) {

		ChatRoom room = new ChatRoom(server, roomName, owner);
		globalRoomList.add(room);
	}

	public synchronized void serverConnected(String serverID, ChatServer server, int port) {
		System.out.println("cached: " + serverID + " "+port);
		serverPortMap.put(serverID, port);
		serverAddrMap.put(serverID, "localhost");
		serverObjectMap.put(serverID, server);
	}

	public synchronized void serverConnected(String serverID, ChatServer server, String address, int port) {
		System.out.println("cached: " + serverID + " "+port);
		serverPortMap.put(serverID, port);
		serverAddrMap.put(serverID, address);
		serverObjectMap.put(serverID, server);
	}

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

	public synchronized ChatRoom joinGlobalChatRoom(String roomName, ClientConnection client) {

		for (ChatRoom room : globalRoomList) {
			if (room.getRoomName().matches(roomName)) {
				room.clientJoin(client);
				return room;
			}
		}
		return null;
	}

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

			//ChatRoom oldRoom = ServerState.getInstance().getChatRoomFromAll(former);
			// Room has not been deleted
			for (ClientConnection oldRoomClient : former.getConnectedClients()) {
				oldRoomClient.getMessageQueue().add(new Message(false, leaverMsg.toJSONString()));
			}
			former.clientLeave(clientConnection);

			//ChatRoom newChatRoom = ServerState.getInstance().getChatRoomFromAll(newRoom);
			for (ClientConnection newRoomClient : newRoom.getConnectedClients()) {
				newRoomClient.getMessageQueue().add(new Message(false, leaverMsg.toJSONString()));
			}
			newRoom.clientJoin(clientConnection);
			clientConnection.currentRoom = newRoom;
			System.out.println(clientConnection.clientID + " join " + newRoom.getRoomName());
		}
		else {
			// join fail, so former room is the same as new room
			clientConnection.getMessageQueue().add(new Message(false, leaverMsg.toJSONString()));
		}
	}

}
