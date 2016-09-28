

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
public class LockReleaser implements Runnable {

	String serverID;
	int port;
	String request;

	public LockReleaser(String serverID, int port, String request)
	{
		this.serverID = serverID;
		this.port = port;
		this.request = request;
	}

	@Override
	public void run()
	{
		String address = ServerState.getInstance().getServerAddrMap().get(serverID);

		try {
			Socket socket = new Socket(InetAddress.getByName(address), port);

			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

			writer.write(request + "\n");
			System.out.println("release request sent to " + serverID+ "::"+ socket.getRemoteSocketAddress());
			writer.flush();


			socket.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
