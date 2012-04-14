package sleet.smtp;

import static sleet.email.EmailRaw.CRLF;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import sleet.TransactionLog;
/**
 * Sends messages to the client.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class ClientWriter {
	/**
	 * The output stream to the client.
	 */
	private final PrintWriter writer;
	
	/**
	 * Records the raw messages that are sent to the client for logging.
	 */
	private final TransactionLog log;

	/**
	 * @param out the output stream to the client
	 * @param log records the raw messages that are sent to the client for logging
	 * @throws IOException
	 */
	public ClientWriter(OutputStream out, TransactionLog log) throws IOException {
		writer = new PrintWriter(out);
		this.log = log;
	}
	
	/**
	 * Sends a message to the client.
	 * @param message the message to send.
	 * @throws IOException
	 */
	public void send(SMTPResponse message) throws IOException{
		send(message.getStatusCode(), message.getMessages());
	}
	
	/**
	 * Sends a message to the client.
	 * @param statusCode the status code
	 * @throws IOException
	 */
	public void send(int statusCode) throws IOException {
		send(statusCode, Arrays.asList(new String[0]));
	}

	/**
	 * Sends a message to the client.
	 * @param statusCode the status code
	 * @param message the message text
	 * @throws IOException
	 */
	public void send(int statusCode, String message) throws IOException {
		send(statusCode, Arrays.asList(new String[]{message}));
	}

	/**
	 * Sends a multiline message to the client. 
	 * @param statusCode the status code
	 * @param messages the text of the messages
	 * @throws IOException
	 */
	public void send(int statusCode, List<String> messages) throws IOException {
		if (messages.isEmpty()){
			send(statusCode + "");
		} else {
			int i = 0;
			for (String message : messages) {
				boolean last = (i == messages.size() - 1);
				String sep = last ? " " : "-";
				
				String fullMessage = statusCode + sep + message;
				send(fullMessage);
				
				i++;
			}
		}
	}
	
	/**
	 * Sends a line to the client.
	 * @param line the line of text to send
	 * @throws IOException
	 */
	private void send(String line) throws IOException{
		log.server(line);
		writer.print(line);
		writer.print(CRLF);
		writer.flush();
	}
}