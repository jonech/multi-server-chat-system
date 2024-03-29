package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.SSLServerSocketFactory;
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
public class ChatServer extends Thread
{

	public ServerSocket listeningSocket;
	public String serverID;
	public String serverAddress;
	public int coordPort;

	// local room list
	private List<ChatRoom> roomList;
	private ChatRoom serverMainHall;
	
	// local clients
	private List<String> localClientIDList;

	// for lock and request from server
	public HashMap<String, String> lockedRoomID;
	public HashMap<String, String> lockedClientID;
	
	
	
	public ChatServer(String serverID, String serverAddress, int clientPort, int coordPort) throws IOException
	{
		// set keystore
		System.setProperty("javax.net.ssl.keyStore", "chjq-keystore");
		System.setProperty("javax.net.ssl.keyStorePassword", "123456");
		System.setProperty("javax.net.ssl.trustStore", "chjq-keystore");
		//System.setProperty("javax.net.debug", "all");
		
		this.serverID = serverID;
		this.serverAddress = serverAddress;
		this.coordPort = coordPort;
		// create socket for clients port
		listeningSocket = SSLServerSocketFactory.getDefault().createServerSocket(
							clientPort, 10, InetAddress.getByName(serverAddress));
		
		roomList = new ArrayList<ChatRoom>();
		localClientIDList = new ArrayList<String>();
		lockedRoomID = new HashMap<String, String>();
		lockedClientID = new HashMap<String, String>();
		
		// create a main hall on the server
		serverMainHall = new ChatRoom(serverID, "MainHall-"+serverID, "SERVER-"+serverID);
	}

	public void run()
	{
		try {
			System.out.println(serverID + " - " +currentThread() + " listening to client on port "+ listeningSocket.getLocalSocketAddress());

			// start listening to servers
			ServerListener listenServer = new ServerListener(serverID, serverAddress, coordPort, this);
			listenServer.start();
			
			selfIntroduce();

			HeartBeatListener listener = new HeartBeatListener(coordPort);
            listener.start();

            HeartBeatSignal heartBeatThread = new HeartBeatSignal();
            heartBeatThread.start();
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
			// remove server from ServerState
			ServerState.getInstance().removeServer(serverID);
			e.printStackTrace();
		}
		finally {
			
			// remove server from ServerState
			ServerState.getInstance().removeServer(serverID);
			
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
	
	/* get the main hall */
	public synchronized ChatRoom getServerMainHall() { return serverMainHall; }
	
	/* put client into Main Hall */
	public synchronized ChatRoom joinMainHall(ClientConnection client)
	{
		serverMainHall.clientJoin(client, "", true);
		return serverMainHall;
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
	public synchronized boolean requestCreateChatRoom(String server, String roomName, String owner) throws InterruptedException
	{
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
		List<LockRequester> threads = new ArrayList<LockRequester>();
		for (HashMap.Entry<String, ChatServerInfo> entry : ServerState.getInstance().getServerInfoMap().entrySet()) {
			// skip if it loops to the ChatServer itself
			if (entry.getKey().matches(serverID) || entry.getKey().equals("auth"))
				continue;
			
			String requestServerID = entry.getKey();
			int requestPort = Integer.parseInt(entry.getValue().port);
			String requestAddress = entry.getValue().address;
			
			//LockRequester t = new LockRequester(entry.getKey(), entry.getValue(), broadcast);
			LockRequester t = new LockRequester(requestServerID, requestAddress, requestPort, broadcast);
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
		for (HashMap.Entry<String, ChatServerInfo> entry : ServerState.getInstance().getServerInfoMap().entrySet()) {
			// skip if it loops to the ChatServer itself
			if (entry.getKey().matches(serverID) || entry.getKey().equals("auth"))
				continue;
			
			String requestServerID = entry.getKey();
			int requestPort = Integer.parseInt(entry.getValue().port);
			String requestAddress = entry.getValue().address;
			
			//LockReleaser releaser = new LockReleaser(entry.getKey(), entry.getValue(), releaseJSON.toJSONString());
			LockReleaser releaser = new LockReleaser(requestServerID, requestAddress, requestPort, releaseJSON.toJSONString());
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
	public synchronized boolean requestJoinServer(String clientID) throws InterruptedException
	{
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
		List<LockRequester> threads = new ArrayList<LockRequester>();
		for (HashMap.Entry<String, ChatServerInfo> entry : ServerState.getInstance().getServerInfoMap().entrySet()) {
			// skip if it loops to the ChatServer itself
			if (entry.getKey().matches(serverID) || entry.getKey().equals("auth"))
				continue;
			
			String requestServerID = entry.getKey();
			int requestPort = Integer.parseInt(entry.getValue().port);
			String requestAddress = entry.getValue().address;
			
			//LockRequester t = new LockRequester(entry.getKey(), entry.getValue(), broadcast);
			LockRequester t = new LockRequester(requestServerID, requestAddress, requestPort, broadcast);
			
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
		for (HashMap.Entry<String, ChatServerInfo> entry : ServerState.getInstance().getServerInfoMap().entrySet()) {
			// skip if it loops to the ChatServer itself
			if (entry.getKey().matches(serverID) || entry.getKey().equals("auth"))
				continue;
			
			String requestServerID = entry.getKey();
			int requestPort = Integer.parseInt(entry.getValue().port);
			String requestAddress = entry.getValue().address;
			
			LockReleaser releaser = new LockReleaser(requestServerID, requestAddress, requestPort, releaseJSON.toJSONString());
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
	
	/**
	 * Request room list from a remote chat server
	 * @return
	 * @throws InterruptedException
	 * @throws ParseException
	 */
	public synchronized List<String> requestRoomList() throws InterruptedException, ParseException
	{
		// make a ROOMLIST JSON broadcast to other server
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMLIST);
		broadcastJSON.put(JSONTag.SERVERID, serverID);
		String broadcast = broadcastJSON.toJSONString();
		
		// create a thread to send ROOMLIST request to server
		List<LongSender> threads = new ArrayList<>();
		for (ChatServerInfo remoteServer : ServerState.getInstance().getRemoteServerInfo()) {
			
			if (remoteServer.id.equals("auth"))
				continue;
			
			String requestServerID = remoteServer.id;
			int requestPort = Integer.parseInt(remoteServer.port);
			String requestAddress = remoteServer.address;
			
			LongSender t = new LongSender(requestServerID, requestAddress, requestPort, broadcast);
			
			t.start();
			threads.add(t);
		}
		
		List<String> remoteRoomList = new ArrayList<>();
		// read the result
		for (LongSender sender : threads) {
			
			// obtain the result of the Requester
			// but release it if the request is not done
			synchronized (sender.result) {
				while (!sender.result.requestDone)
					sender.result.wait();
				
				// crash happended
				if (sender.result.responseMessage == null)
					return null;
				
				JSONObject response = (JSONObject) new JSONParser().parse(sender.result.responseMessage);
				JSONArray rooms = (JSONArray) response.get(JSONTag.ROOMS);

				if (rooms == null) {
					return null;
				}
				
				for (int i=0; i<rooms.size(); i++) {
					remoteRoomList.add((String) rooms.get(i));
				}
			}
		}
		
		return remoteRoomList;
	}
	
	/**
	 * Request other server and see if they have the local chat room
	 * @param roomName
	 * @return
	 * @throws InterruptedException
	 * @throws ParseException
	 */
	public synchronized ChatServerInfo requestRemoteChatRoom(String roomName) throws InterruptedException, ParseException
	{
		// make a ROOMLIST JSON broadcast to other server
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.ROOMEXIST);
		broadcastJSON.put(JSONTag.SERVERID, serverID);
		broadcastJSON.put(JSONTag.ROOMID, roomName);
		String broadcast = broadcastJSON.toJSONString();
		
		// create a thread to send ROOMLIST request to server
		List<LongSender> threads = new ArrayList<>();
		for (ChatServerInfo remoteServer : ServerState.getInstance().getRemoteServerInfo()) {
			
			if (remoteServer.id.equals("auth"))
				continue;
			
			String requestServerID = remoteServer.id;
			int requestPort = Integer.parseInt(remoteServer.port);
			String requestAddress = remoteServer.address;
			
			LongSender t = new LongSender(requestServerID, requestAddress, requestPort, broadcast);
			
			t.start();
			threads.add(t);
		}
		
		// read the result
		for (LongSender sender : threads) {
			
			// obtain the result of the Requester
			// but release it if the request is not done
			synchronized (sender.result) {
				while (!sender.result.requestDone)
					sender.result.wait();
				
				// crash happended
				if (sender.result.responseMessage == null)
					return null;
				
				JSONObject response = (JSONObject) new JSONParser().parse(sender.result.responseMessage);
				String exist = (String) response.get(JSONTag.EXIST);
				
				if (exist.equals(JSONTag.TRUE)) {
					String host = (String) response.get(JSONTag.HOST);
					String port = (String) response.get(JSONTag.PORT);
					String serverId = (String) response.get(JSONTag.SERVERID);
					
					return new ChatServerInfo(serverId, host, port, false);
					
				}
			}
		}
		
		return null;
	}
	
	
	
	/**
	 * For server to introduce itself to other server
	 */
	public synchronized void selfIntroduce()
	{
		// JSON broadcast for introduction
		JSONObject broadcastJSON = new JSONObject();
		broadcastJSON.put(JSONTag.TYPE, JSONTag.NEWSERVERID);
		broadcastJSON.put(JSONTag.SERVERID, this.serverID);
		broadcastJSON.put(JSONTag.HOST, listeningSocket.getInetAddress().getHostAddress());
		broadcastJSON.put(JSONTag.PORT, Integer.toString(coordPort));
		String broadcast = broadcastJSON.toJSONString();
		
		ServerState.getInstance().broadcastShortRemoteServer(serverID, broadcast);
	}
	

}
