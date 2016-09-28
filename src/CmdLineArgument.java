

import org.kohsuke.args4j.Option;

/**
 * Created by jonech on 8/09/2016.
 */
public class CmdLineArgument {

	@Option(required=true, name="-n", aliases={"--name"}, usage="Name of server.")
	private String serverId;

	@Option(required=true, name="-l", usage="Path of the text file containing configuration of server.")
	private String serversConfig;

	public String getServerId() {
		return serverId;
	}

	public String getServersConfig() {
		return serversConfig;
	}
}
