

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by jonech on 8/09/2016.
 */
public class ClientConnection extends Thread {

	private Socket clientSocket;
	private BufferedWriter writer;
	private BufferedReader reader;

	private String serverID;
	private BlockingQueue<Message> messageQueue;
	public String clientID = null;
	public JSONParser parser = new JSONParser();

	// the current room that the client is in
	public ChatRoom currentRoom;

	public ClientConnection(Socket socket, String serverID)
	{

		try {
			this.clientSocket = socket;
			this.serverID = serverID;

			writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF8"));
			reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF8"));
			messageQueue = new LinkedBlockingQueue<>();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run()
	{
		try {
			MessageReader messageReader = new MessageReader(reader, messageQueue);
			messageReader.start();

			while (true) {
				Message message = messageQueue.take();

				if(!message.isFromConnection() && message.getMessage().equals("exit")) {
					// disconnect the client
					Disconnect();
					break;
				}

				// client are forced to leave room by thread
				// this will put client to main hall
				if (!message.isFromConnection() && message.getMessage().equals("leave_room")) {
					ServerState.getInstance().safeChangeRoom(currentRoom, ServerState.getInstance().getServerChatRoom(getName()), this);
				}

				if(message.isFromConnection()) {

					// this is a message for broadcasting
					// anything that assign as this object will be put into the message queue of other ClientConnection
					Message msgForThreads = null;

					JSONObject messageJSONObj = (JSONObject) parser.parse(message.getMessage());
					String requestType = (String) messageJSONObj.get(JSONTag.TYPE);

					/* Client Request NEWIDENTITY */
					if (requestType.equals(JSONTag.NEWIDENTITY)) {

						clientID = (String) messageJSONObj.get(JSONTag.IDENTITY);
						boolean joinSuccess = ServerState.getInstance().getServerObject(serverID).requestJoinServer(clientID);

						// accept or reject the client by responding the JSON message
						JSONObject responseJSON = new JSONObject();
						responseJSON.put(JSONTag.TYPE, JSONTag.NEWIDENTITY);
						if (joinSuccess)
							responseJSON.put(JSONTag.APPROVED, JSONTag.TRUE);
						else
							responseJSON.put(JSONTag.APPROVED, JSONTag.FALSE);
						write(responseJSON.toJSONString());

						if (joinSuccess) {
							// put the client to the main hall
							this.currentRoom = ServerState.getInstance().joinGlobalChatRoom(
									"MainHall-"+getName(), this);

							// create a broadcast to other client
							JSONObject broadcastJSON = new JSONObject();
							broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
							broadcastJSON.put(JSONTag.IDENTITY, clientID);
							broadcastJSON.put(JSONTag.FORMER, "");
							broadcastJSON.put(JSONTag.ROOMID, this.currentRoom.getRoomName());
							// create message for of other client
							msgForThreads = new Message(false, broadcastJSON.toJSONString());
						}
					}

					/* client request room LIST */
					else if (requestType.equals(JSONTag.LIST)) {
						JSONObject response = new JSONObject();
						response.put(JSONTag.TYPE, JSONTag.ROOMLIST);
						JSONArray roomArray = new JSONArray();

						// retrieve chatroom
						for ( ChatRoom room : ServerState.getInstance().getAllGlobalLocalChatRoom()) {
							roomArray.add(room.getRoomName());
						}
						response.put(JSONTag.ROOMS, roomArray);
						// only response to the requested client
						getMessageQueue().add(new Message(false, response.toJSONString()));
					}

					/* Client send a MESSAGE */
					else if (requestType.equals(JSONTag.MESSAGE)) {
						JSONObject broadcast = new JSONObject();
						broadcast.put(JSONTag.TYPE, JSONTag.MESSAGE);
						broadcast.put(JSONTag.IDENTITY, clientID);
						broadcast.put(JSONTag.CONTENT, messageJSONObj.get(JSONTag.CONTENT));
						String broadcastJSON = broadcast.toJSONString();
						msgForThreads = new Message(false, broadcastJSON);
					}

					/* client CREATE a new room */
					else if (requestType.equals(JSONTag.CREATEROOM)) {
						String newRoomID = (String) messageJSONObj.get(JSONTag.ROOMID);
						// request the local server to create room
						boolean success = ServerState.getInstance().getServerObject(serverID).requestCreateChatRoom(getName(), newRoomID, clientID);

						JSONObject response = new JSONObject();
						response.put(JSONTag.TYPE, JSONTag.CREATEROOM);
						response.put(JSONTag.ROOMID, newRoomID);
						// response to client if success or fail to CREATE room
						if (success) {
							response.put(JSONTag.APPROVED, JSONTag.TRUE);
							getMessageQueue().add(new Message(false, response.toJSONString()));
							ChatRoom newRoom = ServerState.getInstance().getServerObject(serverID).getRoom(newRoomID);
							ServerState.getInstance().safeChangeRoom(currentRoom, newRoom, this);
						}
						else {
							response.put(JSONTag.APPROVED, JSONTag.FALSE);
							getMessageQueue().add(new Message(false, response.toJSONString()));
						}

					}

					/* client request to JOIN a room */
					else if (requestType.equals(JSONTag.JOIN)) {
						String newRoomID = (String) messageJSONObj.get(JSONTag.ROOMID);
						boolean joinSuccess = false;

						for (ChatRoom room : ServerState.getInstance().getAllGlobalLocalChatRoom()) {
							// client requested to join a local chat room
							if (room.getRoomName().equals(newRoomID) && room.server.equals(this.serverID)) {
								ServerState.getInstance().safeChangeRoom(currentRoom, room, this);
								joinSuccess = true;
								break;
							}
							// client requested to join a chat room from other server
							else if (room.getRoomName().equals(newRoomID) && !room.server.equals(this.serverID)) {
								routeClient(ServerState.getInstance().getServerObject(room.server), newRoomID);
								joinSuccess = true;
								break;
							}

						}
						// client stays in the same room if can't find the requested room
						if (!joinSuccess)
							ServerState.getInstance().safeChangeRoom(currentRoom, currentRoom, this);

					}

					/* client request MOVEJOIN */
					else if (requestType.equals(JSONTag.MOVEJOIN)) {
						String formerRoom = (String) messageJSONObj.get(JSONTag.FORMER);
						String roomID = (String) messageJSONObj.get(JSONTag.ROOMID);
						String clientID = (String) messageJSONObj.get(JSONTag.IDENTITY);
						moveJoinClient(formerRoom, roomID, clientID);
					}

					/* DELETE room */
					else if (requestType.equals(JSONTag.DELETEROOM)) {
						String roomID = (String) messageJSONObj.get(JSONTag.ROOMID);
						deleteRoom(roomID);
					}

					/* client request WHO */
					else if (requestType.equals(JSONTag.WHO)) {
						JSONObject responseJSON = new JSONObject();
						responseJSON.put(JSONTag.TYPE, JSONTag.ROOMCONTENTS);
						responseJSON.put(JSONTag.ROOMID, currentRoom.getRoomName());

						JSONArray clientArray = new JSONArray();
						for (ClientConnection client : currentRoom.getConnectedClients()) {
							clientArray.add(client.clientID);
						}
						responseJSON.put(JSONTag.IDENTITIES, clientArray);
						responseJSON.put(JSONTag.OWNER, currentRoom.getOwner());
						write(responseJSON.toJSONString());
					}

					/* client request to QUIT */
					else if (requestType.equals(JSONTag.QUIT)) {
						clientQuit();
					}

					if (msgForThreads != null) {
						// put message to the client that is in the current same room
						List<ClientConnection> connectedClients = currentRoom.getConnectedClients();
						for(ClientConnection client : connectedClients) {
							client.getMessageQueue().add(msgForThreads);
						}
					}

				}

				else if (!message.getMessage().equals("leave_room")){
					// If the message is from a thread and it isn't asking to leave room, then
					// it is a message that needs to be sent to the client
					write(message.getMessage());
				}
			}
		}
		catch (InterruptedException e) {
			e.printStackTrace();
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
	}


	public void routeClient(ChatServer routedServer, String roomID)
	{
		JSONObject responseJSON = new JSONObject();
		responseJSON.put(JSONTag.TYPE, JSONTag.ROUTE);
		responseJSON.put(JSONTag.ROOMID, roomID);
		responseJSON.put(JSONTag.HOST, routedServer.listeningSocket.getInetAddress().getHostAddress().toString());
		responseJSON.put(JSONTag.PORT, Integer.toString(routedServer.listeningSocket.getLocalPort()));

		// response to client first
		getMessageQueue().add(new Message(false, responseJSON.toJSONString()));

		// broadcast ROOMCHANGE to everyone in the room
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		broadcastJSON.put(JSONTag.IDENTITY, this.clientID);
		broadcastJSON.put(JSONTag.FORMER, currentRoom.getRoomName());
		broadcastJSON.put(JSONTag.ROOMID, roomID);
		for (ClientConnection client : currentRoom.getConnectedClients()) {
			client.getMessageQueue().add(new Message(false, broadcastJSON.toJSONString()));
		}

		// make the client leave the current room
		currentRoom.clientLeave(this);
	}

	public void moveJoinClient(String former, String roomID, String clientID)
	{
		JSONObject responseJSON = new JSONObject();
		responseJSON.put(JSONTag.TYPE, JSONTag.SERVERCHANGE);
		responseJSON.put(JSONTag.APPROVED, JSONTag.TRUE);
		responseJSON.put(JSONTag.SERVERID, this.serverID);

		this.clientID = clientID;
		// add the client to the local server first
		ServerState.getInstance().getServerObject(getName()).getLocalClientIDList().add(clientID);

		// response to client
		getMessageQueue().add(new Message(false, responseJSON.toJSONString()));

		// find the chat room that the client requested to join
		ChatRoom newRoom = ServerState.getInstance().getChatRoomFromAll(roomID);

		// put the client into the requested room
		if (newRoom != null) {
			newRoom.clientJoin(this);
			this.currentRoom = newRoom;
		}
		else {
			// room got destroy before client join
			// move client to server room
			ChatRoom serverRoom = ServerState.getInstance().getServerChatRoom(getName());
			serverRoom.clientJoin(this);
			this.currentRoom = serverRoom;
		}

		// broadcast ROOMCHANGE to everyone in the room
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		broadcastJSON.put(JSONTag.IDENTITY, clientID);
		broadcastJSON.put(JSONTag.FORMER, former);
		broadcastJSON.put(JSONTag.ROOMID, currentRoom.getRoomName());
		for (ClientConnection client : currentRoom.getConnectedClients()) {
			client.getMessageQueue().add(new Message(false, broadcastJSON.toJSONString()));
		}
	}


	public void deleteRoom(String roomName)
	{
		JSONObject responseJSON = new JSONObject();
		responseJSON.put(JSONTag.TYPE, JSONTag.DELETEROOM);
		responseJSON.put(JSONTag.ROOMID, roomName);

		// check if the client is owner and the room exists
		if (currentRoom.getOwner().matches(clientID) && currentRoom.getRoomName().matches(roomName)) {

			// response to client that approval is TRUE
			responseJSON.put(JSONTag.APPROVED, JSONTag.TRUE);
			getMessageQueue().add(new Message(false, responseJSON.toJSONString()));

			// force everyone to leave room
			for (ClientConnection currentClient : currentRoom.getConnectedClients()) {
				currentClient.getMessageQueue().add(new Message(false, "leave_room"));
			}
			ServerState.getInstance().getServerObject(serverID).removeLocalChatRoom(currentRoom);
		}
		else {

			// response to client that DELETE is NOT approved
			responseJSON.put(JSONTag.APPROVED, JSONTag.FALSE);
			getMessageQueue().add(new Message(false, responseJSON.toJSONString()));
		}

	}

	public void Disconnect()
	{
		// officially remove the client from local client list
		ServerState.getInstance().getServerObject(this.serverID).getLocalClientIDList().remove(this.clientID);
		System.out.println("client disconnected: " + clientSocket.getRemoteSocketAddress());

		// broadcast to all other clients in the room
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		broadcastJSON.put(JSONTag.IDENTITY, this.clientID);
		broadcastJSON.put(JSONTag.FORMER, currentRoom.getRoomName());
		broadcastJSON.put(JSONTag.ROOMID, "");
		for (ClientConnection client : currentRoom.getConnectedClients()) {
			client.getMessageQueue().add(new Message(false, broadcastJSON.toJSONString()));
		}

		// delete room if the current client is the room owner
		if (currentRoom.getOwner().matches(this.clientID)) {
			deleteRoom(currentRoom.getRoomName());
		}
		else {
			currentRoom.clientLeave(this);
		}
	}

	public void clientQuit()
	{
		// When client request to QUIT
		ServerState.getInstance().getServerObject(this.serverID).getLocalClientIDList().remove(this.clientID);
		System.out.println("client disconnected: " + clientSocket.getRemoteSocketAddress());

		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
		broadcastJSON.put(JSONTag.IDENTITY, this.clientID);
		broadcastJSON.put(JSONTag.FORMER, currentRoom.getRoomName());
		broadcastJSON.put(JSONTag.ROOMID, "");

		getMessageQueue().add(new Message(false, broadcastJSON.toJSONString()));
	}


	public BlockingQueue<Message> getMessageQueue() {
		return messageQueue;
	}

	public void write(String msg)
	{
		try {
			writer.write(msg + "\n");
			writer.flush();
			System.out.println(Thread.currentThread() + " - Message sent to client " + clientID);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
