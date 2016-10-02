package au.edu.unimelb.comp90015_chjq.server;

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
		clientList = new ArrayList<>();
		clientList.add(owner);

		clients = new ArrayList<>();
	}

	public String getRoomName() {
		return roomName;
	}

	public String getOwner() {
		return owner;
	}


	public synchronized void clientJoin(ClientConnection client) {
		clients.add(client);
	}

	public synchronized void clientLeave(ClientConnection client)
	{
		clients.remove(client);
	}

	public synchronized List<ClientConnection> getConnectedClients() {
		return clients;
	}
}
