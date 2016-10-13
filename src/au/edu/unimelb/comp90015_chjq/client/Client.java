package au.edu.unimelb.comp90015_chjq.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import org.json.simple.parser.ParseException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

import javax.net.ssl.SSLSocketFactory;

public class Client {

	public static BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

	public static boolean debug = false;


	public static void main(String[] args) throws IOException, ParseException {
		Socket socket = null;
		String identity = null;
		String account = null;
		String password = null;

		State state = new State("", "");
		AccountInfo accountInfo = new AccountInfo("", "", "");

		// set keystore
		System.setProperty("javax.net.ssl.trustStore", "chjq-keystore");


		try {
			//load command line args
			ComLineValues values = new ComLineValues();
			CmdLineParser parser = new CmdLineParser(values);
			try {
				parser.parseArgument(args);
				String hostname = values.getHost();
				identity = values.getIdeneity();
				int port = values.getPort();
				debug = values.isDebug();

				//socket = new Socket(hostname, port);
				socket = SSLSocketFactory.getDefault().createSocket(hostname, port);

			} catch (CmdLineException e) {
				e.printStackTrace();
			}



			if(values.isGoogle()){
				try {
					HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

					// authorization
					Credential credential = OAuth2Verify.authorize();

					// set up global Oauth2 instance
					Oauth2 oauth2 = new Oauth2.Builder(httpTransport, JacksonFactory.getDefaultInstance(), credential).setApplicationName(
							"DistributedChatSystem").build();

					String accessToken = credential.getAccessToken();

					accountInfo = new AccountInfo(null, null, accessToken);

				}catch (IOException e) {
					System.err.println(e);
				} catch (Throwable t) {
					t.printStackTrace();
				}



			}
			else{

				System.out.println("Enter your username:");
				account = reader.readLine();
				System.out.println("Enter your password:");
				char[] p = System.console().readPassword();
				password = new String(p);

				if(debug){
					System.out.println(password);
				}

				accountInfo = new AccountInfo(account, password, null);
			}

			// start sending thread
			MessageSendThread messageSendThread = new MessageSendThread(socket, state, debug, accountInfo);
			Thread sendThread = new Thread(messageSendThread);
			sendThread.start();

			// start receiving thread
			Thread receiveThread = new Thread(new MessageReceiveThread(socket, state, messageSendThread, debug));
			receiveThread.start();

		} catch (UnknownHostException e) {
			System.out.println("Unknown host");
		} catch (IOException e) {
			System.out.println("Communication Error: " + e.getMessage());
		}
	}
}
