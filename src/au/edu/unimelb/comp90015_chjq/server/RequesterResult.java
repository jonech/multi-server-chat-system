package au.edu.unimelb.comp90015_chjq.server;

import org.json.simple.JSONObject;

/**
 * Created by jonech on 15/09/2016.
 *
 * A class that is made just to play as
 * a shared variable role between server and requester
 */
public class RequesterResult
{

	public boolean requestDone = false;
	public boolean canLocked = true;
	
	public String responseMessage;
}
