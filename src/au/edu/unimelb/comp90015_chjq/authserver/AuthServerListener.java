package au.edu.unimelb.comp90015_chjq.authserver;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.Socket;

/**
 * Created by alw on 2016/10/13.
 */
public class AuthServerListener extends Thread {
    private Socket socket;
    private String serverID;
    private AuthServer serverObject;

    public AuthServerListener(Socket socket, String serverid, AuthServer serverObject)
    {
        try {
            this.socket = socket;
            this.serverObject = serverObject;
            this.serverID = serverid;

        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {
            System.out.println(serverID + " - listening to server on port " + socket.getLocalSocketAddress());

                // accept the connection from server
                System.out.println(serverID + " - received server connection: " + socket.getRemoteSocketAddress());

                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // read the request from server
                String request = reader.readLine();
                // parse the request
                JSONObject requestJSON = (JSONObject) new JSONParser().parse(request);
                String requestType = (String) requestJSON.get(JSONTag.TYPE);

                JSONObject responseJSON = new JSONObject();

                if(requestType.matches(JSONTag.LOGIN)){
                    String account = (String)requestJSON.get(JSONTag.ACCOUNT);
                    String password = (String)requestJSON.get(JSONTag.PASSWORD);
                    String accessToken = (String)requestJSON.get(JSONTag.ACCESSTOKEN);

                    responseJSON.put(JSONTag.TYPE, JSONTag.LOGIN);

                    //use password and account
                    if(accessToken.length() < 6){
                        String approved = serverObject.verifyAccount(account, password);
                        responseJSON.put(JSONTag.APPROVED, approved);
                        responseJSON.put(JSONTag.USERNAME, account);

                    }
                    else{
                        String result = serverObject.verifyToken(accessToken);
                        if(result.equals(JSONTag.FALSE)){
                            responseJSON.put(JSONTag.APPROVED, JSONTag.FALSE);

                        }
                        else{
                            responseJSON.put(JSONTag.APPROVED, JSONTag.TRUE);
                            responseJSON.put(JSONTag.USERNAME, result);
                        }
                    }
                }



                // don't bother writing to the connected server if there is nothing to write
                if (responseJSON != null) {
                    writer.write(responseJSON.toJSONString() + "\n");
                    writer.flush();
                    System.out.println(serverID + " - response sent to " + socket.getRemoteSocketAddress());
                }
                // disconnect the server
                socket.close();


        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        finally {
            if(socket != null) {
                try {
                    socket.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
