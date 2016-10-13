package au.edu.unimelb.comp90015_chjq.authserver;

import org.kohsuke.args4j.Option;

/**
 * Created by alw on 2016/8/26.
 */
public class CmdArgs {
    @Option(required = true, name = "-n", usage = "server id")
    private String serverid;

    @Option(required = true, name = "-l", usage = "servers conf file")
    private String servers_conf;

    public String getServerid() {
        return serverid;
    }

    public String getServers_conf() {
        return servers_conf;
    }

    public void setServerid(String serverid) {
        this.serverid = serverid;
    }

    public void setServers_conf(String servers_conf) {
        this.servers_conf = servers_conf;
    }
}
