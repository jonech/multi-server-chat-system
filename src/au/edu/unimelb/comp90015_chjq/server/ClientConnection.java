package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.SSLSocketFactory;
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
			messageQueue = new LinkedBlockingQueue<Message>();
			currentRoom = null;
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
					ChatRoom mainHall = ServerState.getInstance().getServerObject(this.serverID).getServerMainHall();
					this.currentRoom.clientLeave(this, mainHall.getRoomName(), true);
					mainHall.clientJoin(this, this.currentRoom.getRoomName(), false);
					this.currentRoom = mainHall;
					/*
					ServerState.getInstance().safeChangeRoom(currentRoom,
							ServerState.getInstance().getServerObject(getName()).getServerMainHall(), this);*/
				}

				if(message.isFromConnection()) {

					// this is a message for broadcasting
					// anything that assign as this object will be put into the message queue of other ClientConnection
					Message msgForThreads = null;

					String msg = message.getMessage();
					JSONObject messageJSONObj = (JSONObject) parser.parse(msg);
					String requestType = (String) messageJSONObj.get(JSONTag.TYPE);

					/* Client Request LOGIN */
					if(requestType.equals(JSONTag.LOGIN)){

						try{
							Socket socket = SSLSocketFactory.getDefault().createSocket(ServerState.authServerAddr, ServerState.authServerPort);

							BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
							BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
							writer.write(msg+"\n");
							writer.flush();
							String result = reader.readLine();
							write(result);


						}catch (IOException e) {
							//e.printStackTrace();

						}




					}

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
							this.currentRoom = ServerState.getInstance().getServerObject(serverID).joinMainHall(this);
							
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
						
						// retrieve remote server chatroom
						List<String> remoteRoomList = ServerState.getInstance().getServerObject(this.serverID).requestRoomList();
						if (remoteRoomList != null) {
							for (String room : remoteRoomList) {
								roomArray.add(room);
							}
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
							
							ChatRoom newRoom = ServerState.getInstance().getServerObject(this.serverID).getRoom(newRoomID);
							this.currentRoom.clientLeave(this, newRoom.getRoomName(), true);
							newRoom.clientJoin(this, this.currentRoom.getRoomName(), false);
							this.currentRoom = newRoom;
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
								
								this.currentRoom.clientLeave(this, room.getRoomName(), true);
								room.clientJoin(this, this.currentRoom.getRoomName(), false);
								this.currentRoom = room;
								
								//ServerState.getInstance().safeChangeRoom(currentRoom, room, this);
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
						
						// try remote server now
						if (!joinSuccess) {
							
							ChatServerInfo remoteServer = ServerState.getInstance().getServerObject(this.serverID).requestRemoteChatRoom(newRoomID);
							if (remoteServer != null) {
								remoteRouteClient(remoteServer, newRoomID);
								joinSuccess = true;
							}
						}
						
						// client stays in the same room if can't find the requested room
						if (!joinSuccess) {
							
							JSONObject Msg = new JSONObject();
							Msg.put(JSONTag.TYPE, JSONTag.ROOMCHANGE);
							Msg.put(JSONTag.IDENTITY, this.clientID);
							Msg.put(JSONTag.FORMER, this.currentRoom.getRoomName());
							Msg.put(JSONTag.ROOMID, this.currentRoom.getRoomName());
							
							getMessageQueue().add(new Message(false, Msg.toJSONString()));
						}
						
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

					if (msgForThreads != null && currentRoom != null) {
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

	/* routing client within the local servers */
	public void routeClient(ChatServer routedServer, String roomID)
	{
		JSONObject responseJSON = new JSONObject();
		responseJSON.put(JSONTag.TYPE, JSONTag.ROUTE);
		responseJSON.put(JSONTag.ROOMID, roomID);
		responseJSON.put(JSONTag.HOST, routedServer.listeningSocket.getInetAddress().getHostAddress().toString());
		responseJSON.put(JSONTag.PORT, Integer.toString(routedServer.listeningSocket.getLocalPort()));

		// response to client first
		getMessageQueue().add(new Message(false, responseJSON.toJSONString()));
		
		currentRoom.clientLeave(this, roomID, true);
	}
	
	/* routing client to a remote server */
	public void remoteRouteClient(ChatServerInfo remoteServer, String roomID)
	{
		JSONObject responseJSON = new JSONObject();
		responseJSON.put(JSONTag.TYPE, JSONTag.ROUTE);
		responseJSON.put(JSONTag.ROOMID, roomID);
		responseJSON.put(JSONTag.HOST, remoteServer.address);
		responseJSON.put(JSONTag.PORT, remoteServer.port);
		
		getMessageQueue().add(new Message(false, responseJSON.toJSONString()));
		
		currentRoom.clientLeave(this, roomID, true);
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
		ChatRoom newRoom = ServerState.getInstance().findChatRoomFromAll(roomID);

		// put the client into the requested room
		if (newRoom != null) {
			newRoom.clientJoin(this, former, true);
			this.currentRoom = newRoom;
		}
		else {
			// room got destroy before client join
			// move client to server room
			ChatRoom serverRoom = ServerState.getInstance().getServerObject(getName()).getServerMainHall();
			serverRoom.clientJoin(this, former, true);
			this.currentRoom = serverRoom;
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

		// delete room if the current client is the room owner
		// also require self broadcast
		if (currentRoom == null)
			return;
		
		if (currentRoom.getOwner().matches(this.clientID)) {
			currentRoom.clientLeave(this, "", true);
			deleteRoom(currentRoom.getRoomName());
		}
		else {
			currentRoom.clientLeave(this, "", true);
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
