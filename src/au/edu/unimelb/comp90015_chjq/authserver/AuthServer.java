package au.edu.unimelb.comp90015_chjq.authserver;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.ServerSocket;
import java.util.HashMap;

/**
 * Created by alw on 2016/10/13.
 */
public class AuthServer{
    public ServerSocket listeningSocket;
    public String serverID;
    public int coordPort;
    public String serverAddr;

    public AuthServer(String serverID){
        this.serverID = serverID;
        map = new HashMap<String, String>();

    }



    private HashMap<String, String> map;

    public JSONParser parser;

    public static AuthServer init(String serverid, String serverconf){
        AuthServer authServer = new AuthServer(serverid);

        BufferedReader mapReader = new BufferedReader(new InputStreamReader(AuthServer.class.getResourceAsStream("/map.txt")));

        try {
            String line;
            while((line = mapReader.readLine()) != null){
                String[] result = line.split(" ");
                authServer.map.put(result[0], result[1]);
            }
        }catch (Exception e){
            e.printStackTrace();
        }



        try {
            File file = new File(serverconf);
            if (file.isFile() && file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] result = line.split("\t");

                    if (!(result[0].equals(serverid))) {
                        //serverInfos.put(result[0], new ServerInfo(result[0], result[1], Integer.parseInt(result[2]), Integer.parseInt(result[3])));
                        System.out.println("server info: " + line);
                    }
                    else{
                        //myServerInfo = new ServerInfo(result[0], result[1], Integer.parseInt(result[2]), Integer.parseInt(result[3]));

                        authServer.coordPort = Integer.parseInt(result[3]);
                        authServer.serverAddr = result[1];

                    }
                }
                System.out.println("MyserverID:"+ authServer.serverID+
                        " MyserverAddress:"+authServer.serverAddr +
                        " CoordinatePort:"+authServer.coordPort);
            }
            else{
                System.out.println("wrong server_config");
                System.exit(1);
            }





        } catch (Exception e) {
            e.printStackTrace();
        }

        return authServer;

    }

    public void run()
    {
        try{
            listeningSocket = SSLServerSocketFactory.getDefault().createServerSocket(coordPort);
        }catch (IOException e){
            e.printStackTrace();
        }

        try {
            parser = new JSONParser();


            //System.out.println(serverID + " - " +currentThread() + " listening to client on port "+ listeningSocket.getLocalSocketAddress());

            // start listening to servers
            while(true){
                SSLSocket socket = (SSLSocket)listeningSocket.accept();

                System.out.print(socket.getRemoteSocketAddress());

                InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream());
                BufferedReader reader = new BufferedReader(inputStreamReader);

                //Read input from the client and print it to the screen
//                String msg;
//                while ((msg = reader.readLine()) != null) {
//                    System.out.println(msg);
//                }
                AuthServerListener listenServer = new AuthServerListener(socket, this.serverID, this);
                listenServer.start();
            }


        }
        catch (Exception e) {
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

    public String verifyToken(String accessToken){
        try{
            HttpResponse res =
                    GoogleNetHttpTransport.newTrustedTransport().createRequestFactory().buildGetRequest(
                            new GenericUrl("https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token="+accessToken)).execute();

            String userinfo = res.parseAsString();
            JSONObject json = (JSONObject)parser.parse(userinfo);

            String email = (String)json.get("email");
            String name = (String)json.get("name");

            if(name==null || name.equals("")){
                return JSONTag.FALSE;
            }
            else{
                return name;
            }

        }catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return JSONTag.FALSE;
    }

    public String verifyAccount(String account, String password){
        System.out.println(account+" "+password);
        System.out.println(map.get(account));
        if(password.equals(map.get(account))){
            return JSONTag.TRUE;
        }
        else{
            return JSONTag.FALSE;
        }
    }
}
