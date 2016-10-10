package au.edu.unimelb.comp90015_chjq.server;

/**
 * Created by jonech on 10/10/2016.
 */
public class ChatServerInfo
{
	public String address;
	public String port;
	public String id;
	public boolean isLocal;
	
	public ChatServerInfo(String id, String address, String port, boolean isLocal)
	{
		this.address = address;
		this.port = port;
		this.id = id;
		this.isLocal = isLocal;
	}
	
}
