package au.edu.unimelb.comp90015_chjq.authserver;

/**
 * Created by alw on 2016/8/26.
 */
public class ServerInfo {
    public String serverid;
    public String server_address;
    public int clients_port;
    public int coordination_port;

    public ServerInfo(String serverid, String server_address, int clients_port, int coordination_port)
    {
        this.serverid = serverid;
        this.server_address = server_address;
        this.clients_port = clients_port;
        this.coordination_port = coordination_port;
    }

    public String getServerid() {
        return serverid;
    }

    public String getServer_address() {
        return server_address;
    }

    public int getClients_port() {
        return clients_port;
    }

    public int getCoordination_port() {
        return coordination_port;
    }
}
