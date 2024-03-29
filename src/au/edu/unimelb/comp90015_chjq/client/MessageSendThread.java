package au.edu.unimelb.comp90015_chjq.client;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;

import org.json.simple.JSONObject;

public class MessageSendThread implements Runnable {

	private Socket socket;

	private DataOutputStream out;

	private State state;

	private boolean debug;

	private AccountInfo accountInfo;

	// reading from console
	private Scanner cmdin = new Scanner(System.in);

	public MessageSendThread(Socket socket, State state, boolean debug, AccountInfo accountInfo) throws IOException {
		this.socket = socket;
		this.state = state;
		out = new DataOutputStream(socket.getOutputStream());
		this.debug = debug;

		this.accountInfo = accountInfo;
	}

	@Override
	public void run() {

		try {

			// send the #login command
			MessageSend(socket, "#login "
					+accountInfo.getAccount()+" "
					+accountInfo.getPassword()+" "
					+accountInfo.getAccessToken());
		} catch (IOException e1) {
			e1.printStackTrace();
			System.exit(1);
		}

		String id = cmdin.nextLine();
		try {
			MessageSend(socket, "#newidentity "+id);
			// set the id first...
			// crucial part to make sure all the state works after...
			state.setIdentity(id);
		}
		catch (IOException e){

		}



		while (true) {

			String msg = cmdin.nextLine();
			System.out.print("[" + state.getRoomId() + "] " + state.getIdentity() + "> ");
			try {
				MessageSend(socket, msg);
			} catch (IOException e) {
				System.out.println("Communication Error: " + e.getMessage());
				System.exit(1);
			}
		}

	}

	private void send(JSONObject obj) throws IOException {
		if (debug) {
			System.out.println("Sending: " + obj.toJSONString());
			System.out.print("[" + state.getRoomId() + "] " + state.getIdentity() + "> ");
		}
		out.write((obj.toJSONString() + "\n").getBytes("UTF-8"));
		out.flush();
	}

	// send command and check validity
	public void MessageSend(Socket socket, String msg) throws IOException {
		JSONObject sendToServer = new JSONObject();
		String []array = msg.split(" ");
		if(!array[0].startsWith("#")) {
			sendToServer = ClientMessages.getMessage(msg);
			send(sendToServer);
		}
		else if(array.length == 1) {
			if(array[0].startsWith("#list")) {
				sendToServer = ClientMessages.getListRequest();
				send(sendToServer);
			}
			else if(array[0].startsWith("#quit")) {
				sendToServer = ClientMessages.getQuitRequest();
				send(sendToServer);
			}
			else if(array[0].startsWith("#who")) {
				sendToServer = ClientMessages.getWhoRequest();
				send(sendToServer);
			}
			else {
				System.out.println("Invalid command!");
				System.out.print("[" + state.getRoomId() + "] " + state.getIdentity() + "> ");
			}
		}
		else if (array.length == 2) {
			if(array[0].startsWith("#joinroom")) {
				sendToServer = ClientMessages.getJoinRoomRequest(array[1]);
				send(sendToServer);
			}
			else if(array[0].startsWith("#createroom")) {
				sendToServer = ClientMessages.getCreateRoomRequest(array[1]);
				send(sendToServer);
			}
			else if(array[0].startsWith("#deleteroom")) {
				sendToServer = ClientMessages.getDeleteRoomRequest(array[1]);
				send(sendToServer);
			}
			else if (array[0].startsWith("#newidentity")) {
				sendToServer = ClientMessages.getNewIdentityRequest(array[1]);
				send(sendToServer);
			}
			else {
				System.out.println("Invalid command!");
				System.out.print("[" + state.getRoomId() + "] " + state.getIdentity() + "> ");
			}
		}
		else if(array.length == 4){
			if(array[0].startsWith("#login")){
				sendToServer = ClientMessages.getLoginRequest(array[1], array[2], array[3]);
				send(sendToServer);
			}
		}
		else {
			System.out.println("Invalid command!");
			System.out.print("[" + state.getRoomId() + "] " + state.getIdentity() + "> ");
		}

	}

	public void switchServer(Socket temp_socket, DataOutputStream temp_out) throws IOException {
		// switch server initiated by the receiving thread
		// need to use synchronize
		synchronized(out) {
			out.close();
			out = temp_out;
		}
		socket = temp_socket;
	}
}
