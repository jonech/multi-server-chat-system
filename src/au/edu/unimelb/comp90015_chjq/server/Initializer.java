package au.edu.unimelb.comp90015_chjq.server;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by jonech on 8/09/2016.
 */
public class Initializer {

	public static int SERVER_ID_INDEX = 0;
	public static int SERVER_ADDR_INDEX = 1;
	public static int CLIENT_PORT_INDEX = 2;
	public static int COORD_PORT_INDEX = 3;
	public static int LOCATION_INDEX = 4;
	
	public static void main(String args[]) throws CmdLineException {

		// initialize command line argument parser
		CmdLineArgument argsBean = new CmdLineArgument();
		CmdLineParser parser = new CmdLineParser(argsBean);

		// parsing
		try {
			parser.parseArgument(args);
		}
		catch (CmdLineException e) {
			e.printStackTrace();
		}

		System.out.printf("args_input: name = %s, path = %s\n", argsBean.getServerId(), argsBean.getServersConfig());

		try {

			BufferedReader reader = new BufferedReader(new FileReader(argsBean.getServersConfig()));

			String line = null;
			while ((line = reader.readLine()) != null) {
				
				// put the input line into array list
				ArrayList<String> temp = new ArrayList<String>();
				for (String word : line.split("\t")) {
					temp.add(word);
				}
				// retrieve the input column base on the index
				String server_addr = temp.get(SERVER_ADDR_INDEX);
				String server_id = temp.get(SERVER_ID_INDEX);
				int client_port = Integer.parseInt(temp.get(CLIENT_PORT_INDEX));
				int coord_port = Integer.parseInt(temp.get(COORD_PORT_INDEX));
				String location = temp.get(LOCATION_INDEX);
				System.out.print(location.equals("local"));
				// create chat server if its local
				if (location.equals("local")) {
					// create server
					ChatServer server = new ChatServer(server_id, server_addr, client_port, coord_port);
					
					// cache up the server information to server state
					ServerState.getInstance().createGlobalChatRoom(server_id, "MainHall-"+server_id, "SERVER-"+server_id);
					ServerState.getInstance().addLocalServer(server_id, server, server_addr, coord_port);
					
					// start server
					System.out.println("start");
					server.start();
				}
				else {
					ServerState.getInstance().addRemoteServer(server_id, server_addr, coord_port);
				}
				
				for (ChatServer server : ServerState.getInstance().getAllServerObject()) {
					//server;
				}
				
			}

			reader.close();
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
}
