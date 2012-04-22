package sleet.admin;

/**
 * Represents a Sleet admin console response sent from the server to the client.
 * Responses are similar to POP3 responses in that "+OK" and "-ERR" are used.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class AdminResponse {
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
	public AdminResponse(boolean success, String message) {
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
