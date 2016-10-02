package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jonech on 8/09/2016.
 */
public class ChatServer extends Thread {

	public ServerSocket listeningSocket;
	public String serverID;
	public int coordPort;

	// local room list
	private List<ChatRoom> roomList;

	// local clients
	private List<String> localClientIDList;

	// for lock and request from server
	public HashMap<String, String> lockedRoomID;
	public HashMap<String, String> lockedClientID;

	public ChatServer(String serverID, String serverAddress, int clientPort, int coordPort) throws IOException
	{
		this.serverID = serverID;
		this.coordPort = coordPort;
		// create socket for clients port
		listeningSocket = new ServerSocket(clientPort, 10, InetAddress.getByName(serverAddress));

		roomList = new ArrayList<>();
		localClientIDList = new ArrayList<>();
		lockedRoomID = new HashMap<>();
		lockedClientID = new HashMap<>();

		// create a MainHall Room
		ServerState.getInstance().createGlobalChatRoom(serverID, "MainHall-"+serverID, "SERVER-"+serverID);
		ServerState.getInstance().serverConnected(serverID, this, serverAddress, coordPort);
	}

	public void run()
	{
		try {
			System.out.println(serverID + " - " +currentThread() + " listening to client on port "+ listeningSocket.getLocalSocketAddress());

			// start listening to servers
			ServerListener listenServer = new ServerListener(serverID, coordPort, this);
			listenServer.start();

			while (true) {
				// accept new client
				Socket clientSocket = listeningSocket.accept();

				// put client to new client connection thread
				ClientConnection clientConnection = new ClientConnection(clientSocket, serverID);
				System.out.println(clientSocket.getLocalSocketAddress() + ": new client connected from: " +clientSocket.getRemoteSocketAddress());

				// set the thread name as server id
				clientConnection.setName(serverID);
				clientConnection.start();
			}

		}
		catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			if(listeningSocket != null) {
				try {
					listeningSocket.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/* remove the local chat room on the server */
	public synchronized void removeLocalChatRoom(ChatRoom room) {
		roomList.remove(room);
	}

	/* get the list of local chat room on the server */
	public synchronized List<ChatRoom> getLocalRoomList() {
		return roomList;
	}

	/* get the list of connected local client */
	public synchronized List<String> getLocalClientIDList() {
		return localClientIDList;
	}

	/* find the local chat room object from the server */
	public synchronized ChatRoom getRoom(String roomName) {
		for (ChatRoom room : roomList) {
			if (room.getRoomName().matches(roomName)) {
				return room;
			}
		}
		// if server doesn't exist, just return null
		return null;
	}

	/**
	 *  When Client request to create a chat room
	 *  ClientConnection will call this method
	 *  @param server = The server that the client is connected to (redundant but oh well...).
	 *  @param roomName = The chat room name that the client wants.
	 *  @param owner = the client's id that requested to create room (automatically become the owner)
	 */
	public synchronized boolean requestCreateChatRoom(String server, String roomName, String owner) throws InterruptedException {
		boolean chatRoomApproved = true;

		// stop room creation if existing room name in local room
		for (ChatRoom localRoom : getLocalRoomList()) {
			if (localRoom.getRoomName().matches(roomName)) {
				return false;
			}
		}
		// also stop if existing room name is locked on the lock list
		if (lockedRoomID.containsKey(roomName)) {
			return false;
		}

		// make LOCKROOMID broadcast for other ChatServer
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.LOCKROOMID);
		broadcastJSON.put(JSONTag.SERVERID, serverID);
		broadcastJSON.put(JSONTag.ROOMID, roomName);
		String broadcast = broadcastJSON.toJSONString();

		// create the thread for sending LOCKROOMID request to other ChatServer
		List<LockRequester> threads = new ArrayList<>();
		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			// skip if it loops to the ChatServer itself
			if (entry.getKey().matches(serverID))
				continue;
			LockRequester t = new LockRequester(entry.getKey(), entry.getValue(), broadcast);
			t.start();
			threads.add(t);
		}

		// read the result from the thread that sent the request
		for (LockRequester lock : threads) {

			// obtain the result of the Requester
			synchronized (lock.result) {
				// release it if the request is not done, so that it won't hog the shared resource
				while (!lock.result.requestDone)
					lock.result.wait();

				// if one request is false, then we don't need to check anymore...
				if (!lock.result.canLocked) {
					chatRoomApproved = false;
					break;
				}
			}
		}

		// broadcast RELEASEROOMID for other ChatServer
		JSONObject releaseJSON = new JSONObject();
		releaseJSON.put(JSONTag.TYPE, JSONTag.RELEASEROOMID);
		releaseJSON.put(JSONTag.SERVERID, serverID);
		releaseJSON.put(JSONTag.ROOMID, roomName);
		// tell other ChatServer to release the room ID regardless of approved or not
		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			if (entry.getKey().matches(serverID))
				continue;
			LockReleaser releaser = new LockReleaser(entry.getKey(), entry.getValue(), releaseJSON.toJSONString());
			Thread t = new Thread(releaser);
			t.start();
		}

		if (chatRoomApproved) {
			// create a chat room if it is approved
			ChatRoom room = new ChatRoom(server, roomName, owner);
			// add to local chat room list
			roomList.add(room);
			// tell client connection the creation is successful
			return true;
		}

		return false;
	}

	/**
	 * When Client request to join a server
	 * Called by ClientConnection when client request NEWIDENTITY
	 * @param clientID = the clientID that the client wants to use
	 */
	public synchronized boolean requestJoinServer(String clientID) throws InterruptedException {

		// assume it is true first
		boolean clientIDApproved = true;

		// if clientID existed in local server or is temporary locked
		if (localClientIDList.contains(clientID) || lockedClientID.containsKey(clientID)) {
			return false;
		}

		// make a LOCKIDENTITY JSON broadcast to other server
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.LOCKIDENTITY);
		broadcastJSON.put(JSONTag.SERVERID, serverID);
		broadcastJSON.put(JSONTag.IDENTITY, clientID);
		String broadcast = broadcastJSON.toJSONString();

		// create a thread to send a LOCKIDENTITY request to other server
		List<LockRequester> threads = new ArrayList<>();
		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			if (entry.getKey().matches(serverID))
				continue;
			LockRequester t = new LockRequester(entry.getKey(), entry.getValue(), broadcast);
			t.setName(serverID);
			t.start();
			threads.add(t);
		}

		// read the result
		for (LockRequester lock : threads) {

			// obtain the result of the Requester
			// but release it if the request is not done
			synchronized (lock.result) {
				while (!lock.result.requestDone)
					lock.result.wait();

				// if one request is false, then we don't need to check anymore...
				if (!lock.result.canLocked) {
					clientIDApproved = false;
					break;
				}
			}
		}

		// JSON broadcast for other server to RELEASEIDENTITY
		JSONObject releaseJSON = new JSONObject();
		releaseJSON.put(JSONTag.TYPE, JSONTag.RELEASEIDENTITY);
		releaseJSON.put(JSONTag.SERVERID, serverID);
		releaseJSON.put(JSONTag.IDENTITY, clientID);
		// tell other server to release the client ID regardless of approved or not
		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			if (entry.getKey().matches(serverID))
				continue;
			LockReleaser releaser = new LockReleaser(entry.getKey(), entry.getValue(), releaseJSON.toJSONString());
			Thread t = new Thread(releaser);
			t.start();
		}

		if (clientIDApproved) {
			// add the client to local room list
			getLocalClientIDList().add(clientID);
			// return to ClientConnection
			return true;
		}
		else {
			return false;
		}

	}

}
