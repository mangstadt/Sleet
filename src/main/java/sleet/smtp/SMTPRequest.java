package sleet.smtp;

/**
 * Represents a client message.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPRequest {
	/**
	 * The command.
	 */
	private final String command;
	
	/**
	 * The text that comes after the command.
	 */
	private final String parameters;
	
	/**
	 * @param command the command
	 */
	public SMTPRequest(ClientCommand command){
		this(command.name(), null);
	}
	
	public SMTPRequest(String command){
		this(command, null);
	}
	
	/**
	 * @param command the command
	 * @param message the text that comes after the command
	 */
	public SMTPRequest(String command, String message){
		this.command = command;
		this.parameters = message;
	}
	
	public String getCommand(){
		return command;
	}
	
	public String getParameters(){
		return parameters;
	}
	
	@Override
	public String toString(){
		return command + (parameters == null ? "" : " " + parameters);
	}
}
