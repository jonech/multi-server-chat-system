package au.edu.unimelb.comp90015_chjq.server;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.InetAddress;
import javax.net.ssl.SSLSocketFactory;
import org.json.simple.JSONObject;
import javax.net.ssl.SSLSocketFactory;
public class SignalThread extends Thread
{

    private int DEFAULT_SAMPLING_PERIOD = 3000;
    private CountDownLatch endSync;
    private int port;
    private String address;
    private DatagramSocket socket;
    //private BufferedWriter writer;
    //private BufferedReader reader;
    private String serverId;
    private boolean result = false;
    
    public SignalThread(CountDownLatch endSync, int port, String address, String serverId)
    {
        this.endSync = endSync;
        this.port = port;
        this.address = address;
        this.serverId = serverId;
        
        try {

            //System.out.println(serverId + " " + address + " " + port);
            //socket = SSLSocketFactory.getDefault().createSocket(InetAddress.getByName(address), port);
            //writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            //reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            socket = new DatagramSocket();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public boolean getResult() { return  result; }
    
    public String getServerId() { return serverId;}
    
    @Override
    public void run () {
        JSONObject message = new JSONObject();
        message.put("type", "heartbeat");
        
        try {
            System.out.print("Heartbeat to " + serverId + "\n");
            //writer.write(message.toJSONString() + "\n");
            //writer.flush();
            InetAddress serverAddr = InetAddress.getByName(address);
            byte [] mess = (message.toJSONString()+ "\n").getBytes("UTF-8");
            DatagramPacket signal = new DatagramPacket(mess, mess.length, serverAddr, port);
            socket.send(signal);
            byte[] buffer = new byte[1000];
            DatagramPacket rep = new DatagramPacket(buffer, buffer.length);

            socket.setSoTimeout(DEFAULT_SAMPLING_PERIOD);
            socket.receive(rep);
            result = true;
        }
        catch (SocketTimeoutException e) {
            
            e.printStackTrace();
            System.out.println(serverId + "crash");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            
            try {
                if (socket != null)
                    socket.close();
	            //Thread.sleep(200);
                endSync.countDown();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}

