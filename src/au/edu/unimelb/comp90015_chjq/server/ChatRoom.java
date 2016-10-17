package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jonech on 8/09/2016.
 *
 * ChatRoom holds client connection rather than ServerState
 * This is so that it's easier for broadcasting
 * Only need to get the clients within the room itself
 */
public class ChatRoom {

	private String roomName;
	public String server;
	private String owner;

	// list of client id, probably redundant...
	private List<String> clientList;

	// list of local client connection
	private List<ClientConnection> clients;


	public ChatRoom(String server, String roomName, String owner)
	{
		this.server = server;
		this.roomName = roomName;
		this.owner = owner;
		clientList = new ArrayList<String>();
		clientList.add(owner);

		clients = new ArrayList<ClientConnection>();
	}

	public String getRoomName() {
		return roomName;
	}

	public String getOwner() {
		return owner;
	}


	public synchronized void clientJoin(ClientConnection client, String formerRoom, boolean selfBroadcast)
	{
		JSONObject joinMessage = new JSONObject();
		joinMessage.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		joinMessage.put(JSONTag.IDENTITY, client.clientID);
		joinMessage.put(JSONTag.FORMER, formerRoom);
		joinMessage.put(JSONTag.ROOMID, this.roomName);
		
		clients.add(client);
		chatRoomBroadcast(client.clientID, joinMessage.toJSONString(), selfBroadcast);
	}

	public synchronized void clientLeave(ClientConnection client, String newRoom, boolean selfBroadcast)
	{
		JSONObject leaveMessage = new JSONObject();
		leaveMessage.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		leaveMessage.put(JSONTag.IDENTITY, client.clientID);
		leaveMessage.put(JSONTag.FORMER, this.roomName);
		leaveMessage.put(JSONTag.ROOMID, newRoom);
		
		chatRoomBroadcast(client.clientID, leaveMessage.toJSONString(), selfBroadcast);
		clients.remove(client);
	}

	public synchronized List<ClientConnection> getConnectedClients() {
		return clients;
	}
	
	public synchronized void chatRoomBroadcast(String clientID, String message, boolean selfBroadcast)
	{
		for (ClientConnection clientConnection : clients) {
			
			if (!selfBroadcast && clientConnection.clientID.equals(clientID)) {
				continue;
			}
			
			clientConnection.getMessageQueue().add(new Message(false, message));
		}
	}
	
}
