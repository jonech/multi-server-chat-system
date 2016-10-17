package au.edu.unimelb.comp90015_chjq.server;

/**
 * Created by kootuyen on 10/13/2016.
 */
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;


public class HeartBeatSignal extends Thread
{
	private int DEFAULT_SAMPLING_PERIOD = 500;

    @Override
    public void run()
    {
        while (true)
        {
	        HashMap<String, ChatServerInfo> serverInfoMap = new HashMap<>(ServerState.getInstance().getServerInfoMap());
            ArrayList<SignalThread> threadList = new ArrayList<>();
            Iterator iterator = serverInfoMap.entrySet().iterator();
            
            while (iterator.hasNext()) {
                HashMap.Entry<String, ChatServerInfo> pair = (HashMap.Entry<String, ChatServerInfo>)iterator.next();
                if (pair.getValue().isLocal || pair.getValue().id.contains("auth"))
                    iterator.remove();
            }
            
            CountDownLatch endSync = new CountDownLatch(serverInfoMap.size());
            
            for (String serverID : serverInfoMap.keySet()) {
                String address = serverInfoMap.get(serverID).address;
                int port = Integer.parseInt(serverInfoMap.get(serverID).port);
                SignalThread thread = new SignalThread(endSync, port, address, serverID);
                threadList.add(thread);
                thread.start();
            }
            
            try {
                endSync.await();
                for (SignalThread thread : threadList)
                    if (!thread.getResult()) {
                        ServerState.getInstance().removeRemoteServer(thread.getServerId());
                        //System.out.println(thread.getServerId() + " crash");
                    }
                Thread.sleep(DEFAULT_SAMPLING_PERIOD);
            }
            catch (Exception e) {
                e.printStackTrace();
            }


        }
    }
}
