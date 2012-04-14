package sleet.pop3;

/**
 * Represents a POP3 response sent from the server to the client.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class POP3Response {
	/**
	 * True if it was an OK response, false if it was an ERR response.
	 */
	private final boolean success;

	/**
	 * The message.
	 */
	private final String message;

	/**
	 * @param success true if it was an OK response, false if it was an ERR
	 * response.
	 * @param message the message
	 */
	public POP3Response(boolean success, String message) {
		this.success = success;
		this.message = message;
	}

	/**
	 * Gets the message.
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Gets whether the response was an OK or ERR response.
	 * @return true if it was an OK response, false if it was an ERR response.
	 */
	public boolean isSuccess() {
		return success;
	}
}
