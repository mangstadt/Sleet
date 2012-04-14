package sleet.smtp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import sleet.TransactionLog;

/**
 * Reads messages from the client.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class ClientReader {
	/**
	 * Input stream to the client.
	 */
	private final BufferedReader reader;

	/**
	 * Records the raw client messages for logging.
	 */
	private final TransactionLog log;

	/**
	 * True if we are currently in the DATA portion, false if not.
	 */
	private boolean data = false;

	/**
	 * @param in input stream to the client
	 * @param log records the raw client messages for logging
	 * @throws IOException
	 */
	public ClientReader(InputStream in, TransactionLog log) throws IOException {
		reader = new BufferedReader(new InputStreamReader(in));
		this.log = log;
	}

	/**
	 * Gets the next command from the client.
	 * @return the next command
	 * @throws IOException
	 */
	public SMTPRequest next() throws IOException {
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

		if (ClientCommand.DATA.name().equals(command)) {
			data = true;
		}

		return new SMTPRequest(command, parameters);
	}

	/**
	 * Gets the next line from a DATA block.
	 * @return the next DATA line with dot-stuffing removed or null if the end
	 * of the DATA was reached or null if we are not in a DATA block
	 * @throws IOException
	 */
	public String nextDataLine() throws IOException {
		if (!data) {
			return null;
		}

		String line = nextLine();

		//check for end of DATA
		if (".".equals(line)) {
			data = false;
			return null;
		}

		// remove "dot-stuffing"
		//TODO this is disabled because EmailData class expects the dot-stuffing to NOT be removed
//		if (line.startsWith("..")) {
//			line = line.substring(1);
//		}

		return line;
	}

	/**
	 * Reads a line of data from the client.
	 * @return the next line of data
	 * @throws IOException
	 */
	private String nextLine() throws IOException {
		String line = reader.readLine();
		String msg;
		if (line == null) {
			msg = "<client terminated connection>";
		} else {
			msg = line;
		}
		log.client(msg);
		return line;
	}

	/**
	 * Determines if the client is currently sending a DATA block.
	 * @return true if we are in a DATA block, false if not
	 */
	public boolean isInDataBlock() {
		return data;
	}
}