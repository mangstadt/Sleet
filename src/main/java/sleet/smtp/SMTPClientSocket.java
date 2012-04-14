package sleet.smtp;

import static sleet.email.EmailRaw.CRLF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sleet.TransactionLog;
/**
 * Interface that an SMTP client uses to communicate to the SMTP server.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPClientSocket {
	private static final Logger logger = Logger.getLogger(SMTPClientSocket.class.getName());

	/**
	 * The input stream to the server.
	 */
	private final BufferedReader fromServer;

	/**
	 * The output stream to the server.
	 */
	private final PrintWriter toServer;

	/**
	 * Used to parse each message from the server.
	 */
	private final Pattern linePattern = Pattern.compile("^(\\d+)([\\- ])(.*)$");

	/**
	 * Records the SMTP transaction for logging purposes.
	 */
	private final TransactionLog transactionLog;

	/**
	 * True if the initial connection commands were sent, false if not.
	 */
	private boolean connected = false;

	/**
	 * @param in the input stream to the server
	 * @param out the output stream to the server
	 * @throws IOException
	 */
	public SMTPClientSocket(InputStream in, OutputStream out) throws IOException {
		fromServer = new BufferedReader(new InputStreamReader(in));
		toServer = new PrintWriter(out);
		transactionLog = new TransactionLog();
	}

	/**
	 * The server is the one that sends the first message, so this method
	 * retrieves that message. This must be called before any other methods can
	 * be called.
	 * @return the first server message
	 * @throws IOException
	 */
	public SMTPResponse connect() throws IOException {
		if (connected) {
			throw new IllegalStateException("Already connected.");
		}

		SMTPResponse response = receive();
		connected = true;
		return response;
	}

	public SMTPResponse ehlo(String originatingHost) throws IOException {
		return send("EHLO " + originatingHost);
	}

	public SMTPResponse helo(String originatingHost) throws IOException {
		return send("HELO " + originatingHost);
	}

	public SMTPResponse mail(String from) throws IOException {
		return send("MAIL FROM:<" + from + ">");
	}

	public SMTPResponse rcpt(String to) throws IOException {
		return send("RCPT TO:<" + to + ">");
	}

	public SMTPResponse rset() throws IOException {
		return send("RSET");
	}

	/**
	 * Sends the "DATA" command.
	 * @return the SMTP server response
	 * @throws IOException
	 */
	public SMTPResponse data() throws IOException {
		return send("DATA");
	}

	/**
	 * Sends the email data. This must be called *after* {@link #data()} has
	 * been called, because the DATA command must be sent before the actual
	 * data. This method assumes that the data is <b>already sanitized</b> for
	 * sending over the wire.
	 * @param data the <b>already sanitized</b> data--header folding,
	 * dot-stuffing, "&lt;CRLF&gt;" lines, and body wrapping have already been
	 * accounted for. It should not include the terminating ".".
	 * @return the SMTP server response
	 * @throws IOException
	 */
	public SMTPResponse data(String data) throws IOException {
		return send(data + CRLF + ".");
	}

	public SMTPResponse vrfy(String address) throws IOException {
		return send("VRFY " + address);
	}

	public SMTPResponse expn(String address) throws IOException {
		return send("EXPN " + address);
	}

	public SMTPResponse quit() throws IOException {
		return send("QUIT");
	}

	public TransactionLog getTransactionLog() {
		return transactionLog;
	}

	/**
	 * Reads the next message from the server.
	 * @return the next message
	 * @throws IOException
	 */
	private SMTPResponse receive() throws IOException {
		int code = -1;
		List<String> messages = new ArrayList<String>();
		while (true) {
			String line = fromServer.readLine();
			if (line == null) {
				throw new IOException("Server terminated connection.");
			}
			transactionLog.server(line);

			Matcher m = linePattern.matcher(line);
			if (m.find()) {
				if (code == -1) {
					code = Integer.parseInt(m.group(1));
				}

				messages.add(m.group(3));

				if (" ".equals(m.group(2))) {
					//"250-text" means that the message has more lines
					//"250 text" means that the message has no more lines
					break;
				}
			} else {
				logger.warning("Server sent malformed message...ignoring: " + line);
			}
		}

		return new SMTPResponse(code, messages);
	}

	/**
	 * Sends a message to the server.
	 * @param message the message to send
	 * @return the server response
	 * @throws IOException
	 */
	private SMTPResponse send(String message) throws IOException {
		if (!connected) {
			throw new IllegalStateException("Call connect() first.");
		}
		transactionLog.client(message);

		toServer.print(message);
		toServer.print(CRLF);
		toServer.flush();

		return receive();
	}
}
