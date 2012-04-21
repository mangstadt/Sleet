package sleet.smtp;

import static sleet.email.EmailRaw.CRLF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import sleet.TransactionLog;

/**
 * Interface used by an SMTP server to communicate with an SMTP client.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPServerSocket {
	/**
	 * Input stream from the client.
	 */
	private final BufferedReader fromClient;

	/**
	 * Output stream to the client.
	 */
	private final PrintWriter toClient;

	/**
	 * Records conversation for logging purposes.
	 */
	private final TransactionLog transactionLog = new TransactionLog();

	/**
	 * @param fromClient the input stream from the client.
	 * @param toClient the output stream to the client.
	 */
	public SMTPServerSocket(InputStream fromClient, OutputStream toClient) {
		this.fromClient = new BufferedReader(new InputStreamReader(fromClient));
		this.toClient = new PrintWriter(toClient);
	}

	/**
	 * Sends a message to the client without any message text.
	 * @param statusCode the status code
	 * @throws IOException
	 */
	public void sendResponse(int statusCode) throws IOException {
		sendResponse(statusCode, Arrays.asList(new String[0]));
	}

	/**
	 * Sends a message to the client.
	 * @param statusCode the status code
	 * @param message the message text
	 * @throws IOException
	 */
	public void sendResponse(int statusCode, String message) throws IOException {
		sendResponse(statusCode, Arrays.asList(new String[] { message }));
	}

	/**
	 * Sends a multi-lined message to the client.
	 * @param statusCode the status code
	 * @param messages each line of the message text
	 * @throws IOException
	 */
	public void sendResponse(int statusCode, List<String> messages) throws IOException {
		if (messages.isEmpty()) {
			sendLine(statusCode + "");
		} else {
			int i = 0;
			for (String message : messages) {
				boolean last = (i == messages.size() - 1);
				String sep = last ? " " : "-";

				String fullMessage = statusCode + sep + message;
				sendLine(fullMessage);

				i++;
			}
		}
	}

	/**
	 * Sends a line of data to the client.
	 * @param line the line of text to send
	 * @throws IOException
	 */
	private void sendLine(String line) throws IOException {
		transactionLog.server(line);
		toClient.print(line);
		toClient.print(CRLF);
		toClient.flush();
	}

	/**
	 * Gets the next command from the client.
	 * @return the next command
	 * @throws IOException
	 */
	public SMTPRequest nextRequest() throws IOException {
		String line = nextLine();
		if (line == null) {
			throw new IOException("Client terminated connection.");
		}

		//messages with just a command and no text part may contain a trailing space character (see RFC 5321 p.32)
		line = line.trim();

		int space = line.indexOf(' ');
		String command, parameters;
		if (space == -1) {
			command = line;
			parameters = null;
		} else {
			command = line.substring(0, space);
			if (space + 1 < line.length()) {
				parameters = line.substring(space + 1);
			} else {
				parameters = "";
			}
		}

		command = command.toUpperCase();

		return new SMTPRequest(command, parameters);
	}

	/**
	 * Gets the next line from a DATA block.
	 * @return the next line of DATA input from the client or null if there is
	 * no more DATA
	 * @throws IOException
	 */
	public String nextDataLine() throws IOException {
		String line = nextLine();

		//check for end of DATA
		if (".".equals(line)) {
			return null;
		}

		return line;
	}

	/**
	 * Reads a line of data from the client.
	 * @return the next line of data
	 * @throws IOException
	 */
	public String nextLine() throws IOException {
		String line = fromClient.readLine();

		String msg;
		if (line == null) {
			msg = "<client terminated connection>";
		} else {
			msg = line;
		}
		transactionLog.client(msg);

		return line;
	}

	/**
	 * Gets the transaction log
	 * @return the transaction log
	 */
	public TransactionLog getTransactionLog() {
		return transactionLog;
	}
}
