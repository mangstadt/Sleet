package sleet.pop3;

import static sleet.email.EmailRaw.CRLF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import sleet.TransactionLog;
/**
 * Interface used by a POP3 server to communicate with a POP3 client.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class POP3ServerSocket {
	private static final Logger logger = Logger.getLogger(POP3ServerSocket.class.getName());

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
	public POP3ServerSocket(InputStream fromClient, OutputStream toClient) {
		this.fromClient = new BufferedReader(new InputStreamReader(fromClient));
		this.toClient = new PrintWriter(toClient);
	}

	/**
	 * Sends a success ("+OK") response without any message text.
	 */
	public void sendSuccess() {
		sendSuccess(new ArrayList<String>(0));
	}

	/**
	 * Sends a success ("+OK") response.
	 * @param msg the message text
	 */
	public void sendSuccess(String msg) {
		String split[] = msg.split("\\r\\n|\\n");
		sendSuccess(Arrays.asList(split));
	}

	/**
	 * Sends a multi-lined success ("+OK") response.
	 * @param lines the multi-lined message
	 */
	public void sendSuccess(List<String> lines) {
		StringBuilder sb = new StringBuilder();
		sb.append("+OK");

		if (!lines.isEmpty()) {
			sb.append(" " + lines.get(0));
			if (lines.size() > 1) {
				for (int i = 1; i < lines.size(); i++) {
					String msg = lines.get(i);

					//add an extra "." to the beginning of all lines that start with "." (called "dot-stuffing")
					if (msg.startsWith(".")) {
						msg = "." + msg;
					}

					sb.append(CRLF + msg);
				}
				sb.append(CRLF).append(".");
			}
		}

		sb.append(CRLF);

		String response = sb.toString();
		toClient.print(response);
		toClient.flush();

		transactionLog.server(response);
	}

	/**
	 * Sends an error ("-ERR") response.
	 * @param msg the error message
	 * @throws IOException
	 */
	public void sendError(String msg) throws IOException {
		String response = "-ERR " + msg + CRLF;
		toClient.print(response);
		toClient.flush();
		transactionLog.server(response);
	}

	/**
	 * Gets the next request from the client.
	 * @return
	 * @throws IOException
	 */
	public POP3Request nextRequest() throws IOException {
		String line = fromClient.readLine();
		if (line == null) {
			return null;
		}
		transactionLog.client(line);
		line = line.trim();

		String command;
		String message = null;
		int space = line.indexOf(' ');
		if (space == -1) {
			command = line;
		} else {
			command = line.substring(0, space);
			message = line.substring(space + 1);
		}
		command = command.toUpperCase();

		return new POP3Request(command, message);
	}

	/**
	 * Closes the connection.
	 */
	public void close() {
		try {
			fromClient.close();
		} catch (IOException e) {
			logger.log(Level.WARNING, "Problem closing input stream from POP3 client.", e);
		}

		toClient.close();
	}

	public TransactionLog getTransactionLog() {
		return transactionLog;
	}
}
