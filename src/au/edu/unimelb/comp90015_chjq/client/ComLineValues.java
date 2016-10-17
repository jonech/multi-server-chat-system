package au.edu.unimelb.comp90015_chjq.client;
import org.kohsuke.args4j.Option;


public class ComLineValues {
	@Option(required=true, name = "-h", aliases="--host", usage="Server host address")
	private String host;

	@Option(required=false, name="-p", aliases="--port", usage="Server port number")
	private int port = 4444;

	@Option(required=false, name = "-i", aliases="--identity", usage="Client identity")
	private String identity;

	@Option(required=false, name = "-d", aliases="--debug", usage="Debug mode")
	private boolean debug = false;

	@Option(required=false, name= "-g", aliases="--google", usage="Use google account")
	private boolean google=false;



	public boolean isGoogle() {
		return google;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getIdeneity() {
		return identity;
	}

	public boolean isDebug() {
		return debug;
	}
}
