package sleet.admin;

import java.util.Map;

/**
 * Represents an admin console request sent from the client to the server.
 * <p>
 * Request syntax is:<br>
 * <code>COMMAND param1=value param2="value with spaces"</code>
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class AdminRequest {
	/**
	 * The command (e.g. "CREATE_USER").
	 */
	private final String command;

	/**
	 * The command parameters.
	 */
	private final Map<String, String> parameters;

	/**
	 * @param command the command (e.g. "CREATE_USER")
	 * @param parameters the command parameters
	 */
	public AdminRequest(String command, Map<String, String> parameters) {
		this.command = command;
		this.parameters = parameters;
	}

	/**
	 * Gets the command.
	 * @return the command
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Gets the command parameters.
	 * @return the command parameters
	 */
	public Map<String, String> getParameters() {
		return parameters;
	}
}
