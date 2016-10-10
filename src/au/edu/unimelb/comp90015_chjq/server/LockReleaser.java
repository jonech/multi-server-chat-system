package au.edu.unimelb.comp90015_chjq.server;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by jonech on 15/09/2016.
 *
 * This is a task for thread to request other server to release the lock
 * The lock can be for identity or roomID
 * Only depends on the json request string
 */
public class LockReleaser implements Runnable
{
	String serverAddress;
	String serverID;
	int port;
	String request;

	public LockReleaser(String serverID, String serverAddress, int port, String request)
	{
		this.serverAddress = serverAddress;
		this.serverID = serverID;
		this.port = port;
		this.request = request;
	}

	@Override
	public void run()
	{
		//String address = ServerState.getInstance().getServerAddrMap().get(serverID);
		String address = serverAddress;
		
		try {
			// create connection to server
			Socket socket = new Socket(InetAddress.getByName(address), port);
			//Socket socket = SSLSocketFactory.getDefault().createSocket(InetAddress.getByName(address), port);

			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

			// send RELEASE request to server
			writer.write(request + "\n");
			System.out.println("release request sent to " + serverID+ "::"+ socket.getRemoteSocketAddress());
			writer.flush();

			// don't have to wait for anything, can disconnect immediately
			socket.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
