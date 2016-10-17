package au.edu.unimelb.comp90015_chjq.server;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by jonech on 10/10/2016.
 */
public class ShortSender implements Runnable
{
	String serverAddress;
	String serverID;
	int port;
	String request;
	
	public ShortSender(String serverID, String serverAddress, int port, String request)
	{
		this.serverAddress = serverAddress;
		this.serverID = serverID;
		this.port = port;
		this.request = request;
	}
	
	@Override
	public void run()
	{
		String address = serverAddress;
		
		try {
			// create connection to server
			//Socket socket = new Socket(InetAddress.getByName(address), port);
			Socket socket = SSLSocketFactory.getDefault().createSocket(InetAddress.getByName(address), port);
			
			
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			
			// send request to server
			writer.write(request + "\n");
			System.out.println("short request sent to " + serverID+ "::"+ socket.getRemoteSocketAddress());
			writer.flush();
			
			// don't have to wait for anything, can disconnect immediately
			socket.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
