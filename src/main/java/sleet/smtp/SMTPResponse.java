package sleet.smtp;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a server message.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPResponse {
	/**
	 * The status code.
	 */
	private final int statusCode;
	
	/**
	 * The message(s).
	 */
	private final List<String> messages;
	
	/**
	 * @param statusCode the status code
	 */
	public SMTPResponse(int statusCode) {
		this(statusCode, Arrays.asList(new String[0]));
	}
	
	/**
	 * @param statusCode the status code
	 * @param message the message
	 */
	public SMTPResponse(int statusCode, String message) {
		this(statusCode, Arrays.asList(new String[]{message}));
	}

	/**
	 * @param statusCode the status code
	 * @param messages the messages (for a multi-line server message)
	 */
	public SMTPResponse(int statusCode, List<String> messages) {
		this.statusCode = statusCode;
		this.messages = messages;
	}
	
	/**
	 * Gets the status code.
	 * @return the status code
	 */
	public int getStatusCode(){
		return statusCode;
	}
	
	/**
	 * Gets the message.
	 * @return the message
	 */
	public String getMessage(){
		return messages.isEmpty() ? null : messages.get(0);
	}
	
	/**
	 * Gets the messages (for multi-line messages).
	 * @return the messages
	 */
	public List<String> getMessages(){
		return messages;
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(statusCode + "");
		if (!messages.isEmpty()){
			sb.append(": ");
			sb.append(messages.get(0));
			for (int i = 1; i < messages.size(); i++){
				sb.append("\n");
				sb.append(messages.get(i));
			}
		}
		return sb.toString();
	}
}