

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * Default code...
 */
public class MessageReader extends Thread {

	private BufferedReader reader;
	private BlockingQueue<Message> messageQueue;

	public MessageReader(BufferedReader reader, BlockingQueue<Message> messageQueue) {
		this.reader = reader;
		this.messageQueue = messageQueue;
	}

	@Override
	public void run()
	{
		try {
			System.out.println(currentThread() + " : reading message from " + getName());

			String request = null;

			while ((request = reader.readLine()) != null) {
				//place the message in the queue for the client connection thread to process
				Message message = new Message(true, request);
				messageQueue.add(message);
			}

			// If the end of the stream was reached, the client closed the connection
			// Put the exit message in the queue to allow the client connection thread to
			// close the socket
			Message exit = new Message(false, "exit");
			messageQueue.add(exit);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

	}
}
