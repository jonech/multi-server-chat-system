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

            String request= reader.readLine();

            System.out.println(request);
//            while((request= reader.readLine())!=null){
                JSONObject requestJSON = (JSONObject) new JSONParser().parse(request);
                String requestType = (String) requestJSON.get(JSONTag.TYPE);

                JSONObject responseJSON = new JSONObject();

                if(requestType.matches(JSONTag.LOGIN)){
                    String account = (String)requestJSON.get(JSONTag.ACCOUNT);
                    String password = (String)requestJSON.get(JSONTag.PASSWORD);
                    String accessToken = (String)requestJSON.get(JSONTag.ACCESSTOKEN);

                    responseJSON.put(JSONTag.TYPE, JSONTag.LOGIN);

                    //use password and account
                    if(accessToken.length() < 10){
                        System.out.println("username login");
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

            String a = responseJSON.toJSONString()+"\n";
                System.out.println(a);
                writer.write(a);
                writer.flush();
                socket.close();

//            }

                // read the request from server
                 ;
                // parse the request







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
