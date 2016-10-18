package au.edu.unimelb.comp90015_chjq.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Created by kootuyen on 10/18/2016.
 */
public class HeartBeatListener extends Thread{
    DatagramSocket socket;
    int port;

    public HeartBeatListener(int port) {
        this.port = port;
    }
    @Override
    public void run()
    {
        try {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[1000];
            
            while (true) {
                DatagramPacket heartBeat = new DatagramPacket(buffer, buffer.length);
                socket.receive(heartBeat);
                DatagramPacket reply = new DatagramPacket(heartBeat.getData(), heartBeat.getLength()
                                                            ,heartBeat.getAddress(),heartBeat.getPort());
                socket.send(reply);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (socket != null)
                socket.close();
        }

    }

}
