package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by jonech on 15/09/2016.
 * This is a 'task' for thread to listen to other server
 * When it gets a request, it will do the appropriate stuff
 */
public class ServerListener extends Thread {

	private ServerSocket listeningSocket;
	private String serverID;
	private ChatServer serverObject;

	public ServerListener(String serverID, String address, int coordinationPort, ChatServer server)
	{
		try {

			this.serverID = serverID;
			this.serverObject = server;

			//String address = ServerState.getInstance().getServerAddrMap().get(serverID);

			//listeningSocket = new ServerSocket(coordinationPort, 10, InetAddress.getByName(address));
			listeningSocket = SSLServerSocketFactory.getDefault().createServerSocket(
					coordinationPort, 10, InetAddress.getByName(address));
		}

		catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {

		try {
			System.out.println(serverID + " - listening to server on port "+ listeningSocket.getLocalSocketAddress());
			while (true) {
				// accept the connection from server
				Socket server = listeningSocket.accept();
				System.out.println(serverID + " - received server connection: " + server.getRemoteSocketAddress());

				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(server.getOutputStream()));
				BufferedReader reader = new BufferedReader(new InputStreamReader(server.getInputStream()));

				// read the request from server
				String request = reader.readLine();
				// parse the request
				JSONObject requestJSON = (JSONObject) new JSONParser().parse(request);
				String requestType = (String) requestJSON.get(JSONTag.TYPE);

				JSONObject responseJSON = new JSONObject();

				/* server request to LOCKIDENTITY */
				if (requestType.matches(JSONTag.LOCKIDENTITY)) {

					String clientID = (String) requestJSON.get(JSONTag.IDENTITY);
					String requestServer = (String) requestJSON.get(JSONTag.SERVERID);
					// lock the clientID
					serverObject.lockedClientID.put(clientID, requestServer);

					responseJSON.put(JSONTag.TYPE, JSONTag.LOCKIDENTITY);
					responseJSON.put(JSONTag.IDENTITY, clientID);
					responseJSON.put(JSONTag.SERVERID, serverID);

					if (serverObject.getLocalClientIDList().contains(clientID))
						responseJSON.put(JSONTag.LOCKED, JSONTag.FALSE);
					else
						responseJSON.put(JSONTag.LOCKED, JSONTag.TRUE);

				}

				/* server request to LOCKROOMID */
				else if (requestType.matches(JSONTag.LOCKROOMID)) {

					String roomID = (String) requestJSON.get(JSONTag.ROOMID);
					String requestServer = (String) requestJSON.get(JSONTag.SERVERID);
					// lock the RoomID
					serverObject.lockedRoomID.put(roomID, requestServer);

					responseJSON.put(JSONTag.TYPE, JSONTag.LOCKROOMID);
					responseJSON.put(JSONTag.ROOMID, roomID);
					responseJSON.put(JSONTag.SERVERID, serverID);
					// default TRUE
					responseJSON.put(JSONTag.LOCKED, JSONTag.TRUE);
					for (ChatRoom localRoom : serverObject.getLocalRoomList()) {
						if (localRoom.getRoomName().matches(roomID)) {
							// override LOCKED to FALSE
							responseJSON.put(JSONTag.LOCKED, JSONTag.FALSE);
							break;
						}
					}
				}

				/* server request to RELEASEIDENTITY */
				else if (requestType.matches(JSONTag.RELEASEIDENTITY)) {

					String clientID = (String) requestJSON.get(JSONTag.IDENTITY);
					String requestServer = (String) requestJSON.get(JSONTag.SERVERID);
					System.out.println(serverID + " - received release id from " + requestServer);
					if (serverObject.lockedClientID.get(clientID).matches(requestServer)) {
						serverObject.lockedClientID.remove(clientID);
						System.out.println(clientID + " released!");
					}
					responseJSON = null;
				}

				/* server request to RELEASEROOMID */
				else if (requestType.matches(JSONTag.RELEASEROOMID)) {

					String roomID = (String) requestJSON.get(JSONTag.ROOMID);
					String requestServer = (String) requestJSON.get(JSONTag.SERVERID);
					System.out.println(serverID + " - received release room from " + requestServer);
					if (serverObject.lockedRoomID.get(roomID).matches(requestServer)) {
						serverObject.lockedRoomID.remove(roomID);
						System.out.println(roomID+  " released!");
					}
					responseJSON = null;
				}
				
				/* server self-introduce NEWSERVERID */
				else if (requestType.matches(JSONTag.NEWSERVERID)) {
					String newServerID = (String) requestJSON.get(JSONTag.SERVERID);
					String host = (String) requestJSON.get(JSONTag.HOST);
					String port = (String) requestJSON.get(JSONTag.PORT);
					
					// if server does not exist, add it
					ServerState.getInstance().addRemoteServer(newServerID, host, Integer.parseInt(port));
					responseJSON = null;
				}

				else if (requestType.matches(JSONTag.HEARTBEAT)) {
					responseJSON.put(JSONTag.TYPE, JSONTag.HEARTBEAT);
					System.out.print("HeartBeat Replied.");
				}
				
				// don't bother writing to the connected server if there is nothing to write
				if (responseJSON != null) {
					writer.write(responseJSON.toJSONString() + "\n");
					writer.flush();
					System.out.println(serverID + " - response sent to " + server.getRemoteSocketAddress());
				}
				// disconnect the server
				server.close();
			}


		}
		catch (IOException e) {
			e.printStackTrace();
		}
		catch (ParseException e) {
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
}

