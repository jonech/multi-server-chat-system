

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

	private List<ChatRoom> roomList;

	private List<String> localClientIDList;

	public HashMap<String, String> lockedRoomID;
	public HashMap<String, String> lockedClientID;

	public ChatServer(String serverID, String serverAddress, int clientPort, int coordPort) throws IOException
	{
		this.serverID = serverID;
		this.coordPort = coordPort;
		listeningSocket = new ServerSocket(clientPort, 10, InetAddress.getByName(serverAddress));

		roomList = new ArrayList<>();
		localClientIDList = new ArrayList<>();
		lockedRoomID = new HashMap<>();
		lockedClientID = new HashMap<>();

		// create a MainHall Room
		ServerState.getInstance().createGlobalChatRoom(serverID, "MainHall-"+serverID, "SERVER-"+serverID);
		ServerState.getInstance().serverConnected(serverID, this, serverAddress, coordPort);
	}

	public ChatServer(String serverID, int clientPort, int coordPort) throws IOException
	{
		this.serverID = serverID;
		this.coordPort = coordPort;
		listeningSocket = new ServerSocket(clientPort);

		roomList = new ArrayList<>();
		localClientIDList = new ArrayList<>();
		lockedRoomID = new HashMap<>();
		lockedClientID = new HashMap<>();

		// create a MainHall Room
		ServerState.getInstance().createGlobalChatRoom(serverID, "MainHall-"+serverID, "SERVER-"+serverID);
		ServerState.getInstance().serverConnected(serverID, this, coordPort);
	}

	public void run()
	{
		try {
			System.out.println(serverID + " - " +currentThread() + " listening to client on port "+ listeningSocket.getLocalSocketAddress());

			// start listening to servers
			//Thread listenServer = new Thread(new ServerListener(serverID, coordPort, this));
			ServerListener listenServer = new ServerListener(serverID, coordPort, this);
			listenServer.start();

			while (true) {
				// accept new client
				Socket clientSocket = listeningSocket.accept();

				ClientConnection clientConnection = new ClientConnection(clientSocket, serverID);
				System.out.println(clientSocket.getLocalSocketAddress() + ": new client connected from: " +clientSocket.getRemoteSocketAddress());

				// set the thread name as server id
				clientConnection.setName(serverID);
				clientConnection.start();

				// Register the new connection with the client manager
				//ServerState.getInstance().clientConnected(clientConnection);
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


	public synchronized void removeLocalChatRoom(ChatRoom room) {
		roomList.remove(room);
	}

	public synchronized List<ChatRoom> getLocalRoomList() {
		return roomList;
	}

	public synchronized List<String> getLocalClientIDList() {
		return localClientIDList;
	}

	public synchronized ChatRoom getRoom(String roomName) {
		for (ChatRoom room : roomList) {
			if (room.getRoomName().matches(roomName)) {
				return room;
			}
		}

		return null;
	}

	public synchronized boolean requestCreateChatRoom(String server, String roomName, String owner) throws InterruptedException {
		boolean chatRoomApproved = true;

		// stop room creation if existing room name in local room
		for (ChatRoom localRoom : getLocalRoomList()) {
			if (localRoom.getRoomName().matches(roomName)) {
				return false;
			}
		}
		// also stop if existing room name is locked
		if (lockedRoomID.containsKey(roomName)) {
			return false;
		}

		List<LockRequester> threads = new ArrayList<>();
		// make LOCKROOMID broadcast
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.LOCKROOMID);
		broadcastJSON.put(JSONTag.SERVERID, serverID);
		broadcastJSON.put(JSONTag.ROOMID, roomName);
		String broadcast = broadcastJSON.toJSONString();

		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			if (entry.getKey().matches(serverID))
				continue;
			LockRequester t = new LockRequester(entry.getKey(), entry.getValue(), broadcast);
			t.start();
			threads.add(t);
		}

		for (LockRequester lock : threads) {

			// obtain the result of the Requester
			// but release it if the request is not done
			synchronized (lock.result) {
				while (!lock.result.requestDone)
					lock.result.wait();

				// if one request is false, then we don't need to check anymore...
				if (!lock.result.canLocked) {
					chatRoomApproved = false;
					break;
				}
			}
		}

		JSONObject releaseJSON = new JSONObject();
		releaseJSON.put(JSONTag.TYPE, JSONTag.RELEASEROOMID);
		releaseJSON.put(JSONTag.SERVERID, serverID);
		releaseJSON.put(JSONTag.ROOMID, roomName);

		// tell other server to release the room ID regardless of approved or not
		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			if (entry.getKey().matches(serverID))
				continue;
			LockReleaser releaser = new LockReleaser(entry.getKey(), entry.getValue(), releaseJSON.toJSONString());
			Thread t = new Thread(releaser);
			t.start();
		}

		if (chatRoomApproved) {
			ChatRoom room = new ChatRoom(server, roomName, owner);
			roomList.add(room);
			return true;
		}
		return false;
	}

	public synchronized boolean requestJoinServer(String clientID) throws InterruptedException {
		boolean clientIDApproved = true;

		// if clientID existed in local server or is temporary locked
		if (localClientIDList.contains(clientID) || lockedClientID.containsKey(clientID)) {
			return false;
		}

		List<LockRequester> threads = new ArrayList<>();
		// make a LOCKIDENTITY broadcast
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.LOCKIDENTITY);
		broadcastJSON.put(JSONTag.SERVERID, serverID);
		broadcastJSON.put(JSONTag.IDENTITY, clientID);
		String broadcast = broadcastJSON.toJSONString();

		for (HashMap.Entry<String, Integer> entry : ServerState.getInstance().getServerPortMap().entrySet()) {
			if (entry.getKey().matches(serverID))
				continue;
			LockRequester t = new LockRequester(entry.getKey(), entry.getValue(), broadcast);
			t.setName(serverID);
			t.start();
			threads.add(t);
		}

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
			getLocalClientIDList().add(clientID);
			return true;
		}
		else {
			return false;
		}

	}

}
