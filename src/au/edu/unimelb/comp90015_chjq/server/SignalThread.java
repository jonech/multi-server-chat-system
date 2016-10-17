package au.edu.unimelb.comp90015_chjq.server;
import java.util.concurrent.CountDownLatch;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import org.json.simple.JSONObject;
public class SignalThread extends Thread{
    private int DEFAULT_SAMPLING_PERIOD = 20000;
    private CountDownLatch endSync;
    private int port;
    private String address;
    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private String serverId;
    private boolean result = false;
    public SignalThread(CountDownLatch endSync, int port, String address, String serverId) {
        this.endSync = endSync;
        this.port = port;
        this.address = address;
        this.serverId = serverId;
        try {
            socket = new Socket(address, port);
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    boolean getResult() {return  result;}
    String getServerId() { return serverId;}
    @Override
    public void run () {
        JSONObject message = new JSONObject();
        message.put("type", "heartbeat");
        try {
            System.out.print("Heartbeat to " + serverId + "\n");
            writer.write(message.toJSONString() + "\n");
            writer.flush();
            Thread.sleep(DEFAULT_SAMPLING_PERIOD);
            if (reader.readLine() != null)
                result = true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
                endSync.countDown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}

