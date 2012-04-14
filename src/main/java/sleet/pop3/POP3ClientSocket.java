package sleet.pop3;

import static sleet.email.EmailRaw.CRLF;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;

import sleet.TransactionLog;

/**
 * Interface used by a POP3 client to communicate with a POP3 server.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class POP3ClientSocket {
	private static final Logger logger = Logger.getLogger(POP3ClientSocket.class.getName());

	/**
	 * Input stream from the server.
	 */
	private final BufferedReader fromServer;

	/**
	 * Output stream to the server.
	 */
	private final PrintWriter toServer;

	/**
	 * True if the client has connected to the server, false if not.
	 */
	private boolean connected = false;

	/**
	 * The string used to create the APOP hash.
	 */
	private String apopHashString;

	/**
	 * Records the conversation for logging purposes.
	 */
	private final TransactionLog transactionLog = new TransactionLog();

	/**
	 * @param fromServer the input stream from the server
	 * @param toServer the output stream to the server
	 */
	public POP3ClientSocket(InputStream fromServer, OutputStream toServer) {
		this.fromServer = new BufferedReader(new InputStreamReader(fromServer));
		this.toServer = new PrintWriter(toServer);
	}

	/**
	 * The server is the one that sends the first message, so this method
	 * retrieves that message. Afterwards, communication follows a typical
	 * client/server request/response pattern.
	 * @return the first server message
	 * @throws IOException if there was a problem with the socket
	 * @throws POP3ErrorException if an error was returned from the server
	 */
	public POP3Response connect() throws IOException, POP3ErrorException {
		if (connected) {
			throw new IllegalStateException("Already connected.");
		}

		POP3Response response = receive(false);

		//get the string used for hash in APOP request 
		String message = response.getMessage();
		Pattern p = Pattern.compile("<.*?>");
		Matcher m = p.matcher(message);
		if (m.find()) {
			apopHashString = m.group(0);
		}

		connected = true;
		return response;
	}

	public POP3Response user(String userName) throws IOException, POP3ErrorException {
		return send("USER " + userName);
	}

	public POP3Response pass(String password) throws IOException, POP3ErrorException {
		return send("PASS " + password);
	}

	/**
	 * Sends an APOP authentication request.
	 * @param userName the user name
	 * @param password the plaintext password
	 * @return the response
	 * @throws IOException
	 * @throws POP3ErrorException
	 * @throws IllegalStateException if the server did not send a APOP hash
	 * string
	 */
	public POP3Response apop(String userName, String password) throws IOException, POP3ErrorException {
		if (apopHashString == null) {
			throw new IllegalStateException("APOP command cannot be used because server did not send a hash string.");
		}

		String hash = DigestUtils.md5Hex(apopHashString + password);
		return send("APOP " + userName + " " + hash);
	}

	public POP3Response quit() throws IOException, POP3ErrorException {
		return send("QUIT");
	}

	public POP3Response stat() throws IOException, POP3ErrorException {
		return send("STAT");
	}

	public POP3Response list() throws IOException, POP3ErrorException {
		return send("LIST", true);
	}

	public POP3Response list(int msg) throws IOException, POP3ErrorException {
		return send("LIST " + msg);
	}

	public POP3Response retr(int msg) throws IOException, POP3ErrorException {
		return send("RETR " + msg, true);
	}

	public POP3Response dele(int msg) throws IOException, POP3ErrorException {
		return send("DELE " + msg);
	}

	public POP3Response noop() throws IOException, POP3ErrorException {
		return send("NOOP");
	}

	public POP3Response rset() throws IOException, POP3ErrorException {
		return send("RSET");
	}

	public POP3Response top(int msg, int lines) throws IOException, POP3ErrorException {
		return send("TOP " + msg + " " + lines, true);
	}

	public POP3Response uidl() throws IOException, POP3ErrorException {
		return send("UIDL", true);
	}

	public POP3Response uidl(int msg) throws IOException, POP3ErrorException {
		return send("UIDL " + msg);
	}

	private POP3Response send(String cmd) throws IOException, POP3ErrorException {
		return send(cmd, false);
	}

	/**
	 * Sends a request to the server.
	 * @param msg the request message
	 * @param multiLineResponse true if the response is expected to be
	 * multi-lined, false if the response is expected to be just a single line
	 * @return the server response
	 * @throws IOException
	 * @throws POP3ErrorException if an error was returned from the server
	 */
	private POP3Response send(String msg, boolean multiLineResponse) throws IOException, POP3ErrorException {
		if (!connected) {
			connect();
		}

		writeLine(msg);

		return receive(multiLineResponse);
	}

	/**
	 * Receives the next server message.
	 * @param multiLine true if the response is expected to be multi-lined,
	 * false if the response is expected to be just a single line
	 * @return the response
	 * @throws IOException
	 * @throws POP3ErrorException if an error was returned from the server
	 */
	private POP3Response receive(boolean multiLine) throws IOException, POP3ErrorException {
		String line = readLine();
		line = line.trim();

		int space = line.indexOf(' ');

		//get status
		String status;
		if (space == -1) {
			status = line;
		} else {
			status = line.substring(0, space);
		}
		status = status.toUpperCase();
		boolean success = status.equals("+OK");

		//get first line of message
		StringBuilder message = new StringBuilder();
		if (space >= 0) {
			message.append(line.substring(space + 1));
		}

		//throw exception on error response
		if (!success) {
			throw new POP3ErrorException(new POP3Response(success, message.toString()));
		}

		//get other lines of message if message is multi-lined
		if (multiLine) {
			while (true) {
				line = readLine();

				//check for end of message
				if (".".equals(line)) {
					break;
				}

				//remove dot-stuffing
				if (line.startsWith("..")) {
					line = line.substring(1);
				}

				message.append(line);
				message.append(CRLF);
			}
		}

		return new POP3Response(success, message.toString());
	}

	private void writeLine(String line) {
		toServer.write(line);
		toServer.write(CRLF);
		toServer.flush();
		transactionLog.client(line + CRLF);
	}

	private String readLine() throws IOException {
		String line = fromServer.readLine();
		if (line == null) {
			throw new IOException("Connection to server prematurely ended.");
		}
		transactionLog.server(line + CRLF);
		return line;
	}

	/**
	 * Closes the POP3 session with the server.
	 * @throws IOException
	 */
	public void close() throws IOException {
		try {
			quit();
		} catch (POP3ErrorException e) {
			logger.log(Level.WARNING, "Problem terminating POP3 session.", e);
		} finally {
			try {
				fromServer.close();
			} catch (IOException e) {
				logger.log(Level.WARNING, "Problem closing input stream to POP3 server.", e);
			}

			toServer.close();
		}
	}
}
