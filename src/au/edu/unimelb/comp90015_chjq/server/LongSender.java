package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Created by jonech on 15/10/2016.
 */
public class LongSender extends Thread
{
	
	public int serverPort;
	public String serverAddress;
	public String serverID;
	public String request;
	
	public RequesterResult result = new RequesterResult();
	
	public LongSender(String serverID, String serverAddress, int port, String request)
	{
		serverPort = port;
		this.serverAddress = serverAddress;
		this.serverID = serverID;
		this.request = request;
	}
	
	@Override
	public void run()
	{
		
		try {
			//String address = ServerState.getInstance().getServerAddrMap().get(serverID);
			String address = serverAddress;
			
			// make sure no other thread is accessing the Result object
			synchronized (result) {
				// create connection to the server
				Socket socket = SSLSocketFactory.getDefault().createSocket(InetAddress.getByName(address), serverPort);
				
				System.out.println(getName() + " - connected to " + serverID+ "::" + socket.getRemoteSocketAddress());
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				
				// send request to the server
				writer.write(request + "\n");
				System.out.println(getName() + " - request sent to " + serverID+ "::" + socket.getRemoteSocketAddress());
				writer.flush();
				
				String response = null;
				while ((response = reader.readLine()) != null) {
					
					System.out.println(getName() + " - received from " + serverID+ "::" + socket.getRemoteSocketAddress());
					JSONObject responseJSON = (JSONObject) new JSONParser().parse(response);
					
					// retrieve the result
					result.responseMessage = responseJSON.toJSONString();
					result.requestDone = true;
					// release the Result object
					result.notify();
					break;
				}
				socket.close();
			}
			
		}
		catch (IOException e) {
			e.printStackTrace();
			result.responseMessage = null;
			result.requestDone = true;
			result.notify();
		}
		catch (ParseException e) {
			e.printStackTrace();
		}
	}
	
}
