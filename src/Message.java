

/**
 * Created by jonech on 8/09/2016.
 */
public class Message {

	// True if the message comes from a connection, false if it comes from a thread
	private boolean isFromConnection = false;

	private String message;

	public Message(boolean isFromConnection, String message) {
		super();
		this.isFromConnection = isFromConnection;
		this.message = message;
	}


	public boolean isFromConnection() {
		return isFromConnection;
	}
	public String getMessage() {
		return message;
	}
}
