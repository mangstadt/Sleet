package sleet.smtp;

/**
 * A list of all supported SMTP commands.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public enum ClientCommand {
	HELO, EHLO, MAIL, RCPT, QUIT, DATA, VRFY, EXPN, RSET, HELP, NOOP
}
