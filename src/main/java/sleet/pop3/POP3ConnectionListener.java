package sleet.pop3;

import static sleet.email.EmailRaw.CRLF;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;

import sleet.db.DbDao;
import sleet.db.POPEmail;
import sleet.db.User;
import sleet.email.EmailData;
/**
 * Listens for and handles POP3 client connections. Forks each client/server
 * connection into its own thread.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class POP3ConnectionListener {
	private static final Logger logger = Logger.getLogger(POP3ConnectionListener.class.getName());
	private final DbDao dao;
	private boolean started = false;
	private int port = 110;
	private String hostName;
	private File transactionLogFile;
	private final Map<String, User> loggedInUsers = Collections.synchronizedMap(new HashMap<String, User>());

	public POP3ConnectionListener(DbDao dao) {
		this.dao = dao;
	}

	public void setPort(int port) {
		if (started) {
			throw new IllegalStateException("POP3 server properties cannot be changed once the server starts.");
		}
		if (port < 1) {
			throw new IllegalArgumentException("Port must be a postive integer.");
		}
		this.port = port;
	}

	public void setHostName(String hostName) {
		if (started) {
			throw new IllegalStateException("POP3 server properties cannot be changed once the server starts.");
		}
		if (hostName == null) {
			throw new IllegalArgumentException("Host name cannot be null.");
		}
		this.hostName = hostName;
	}

	public void setTransactionLogFile(File transactionLogFile) {
		if (started) {
			throw new IllegalStateException("Transaction log file cannot be changed once the server starts.");
		}
		this.transactionLogFile = transactionLogFile;
	}

	public void start() throws IOException {
		if (hostName == null) {
			throw new IllegalStateException("Host name must be set.");
		}

		started = true;

		ServerSocket serverSocket = new ServerSocket(port);
		logger.info("Ready to receive POP3 requests on port " + port + "...");

		while (true) {
			Socket socket = serverSocket.accept();
			logger.info("POP3 connection established with " + socket.getInetAddress().getHostAddress());
			POP3Conversation thread = new POP3Conversation(socket);
			thread.start();
		}
	}

	private class POP3Conversation extends Thread {
		private final Socket socket;
		private final POP3ServerSocket serverSocket;
		private List<POPEmail> popEmails;
		private User currentUser;
		private boolean authenticated = false;

		public POP3Conversation(Socket socket) throws IOException {
			this.socket = socket;
			this.serverSocket = new POP3ServerSocket(socket.getInputStream(), socket.getOutputStream());
		}

		@Override
		public void run() {
			try {
				//send welcome message
				long pid = getId();
				long clock = System.currentTimeMillis();
				String hashString = "<" + pid + "." + clock + "@" + hostName + ">";
				serverSocket.sendSuccess("Sleet POP3 server ready " + hashString);

				POP3Request request;
				while ((request = serverSocket.nextRequest()) != null) {
					String cmd = request.getCommand();
					String params = request.getParameters();

					if ("APOP".equals(cmd)) {
						//RFC 1939, p.15
						if (authenticated) {
							serverSocket.sendError("Already authenticated.");
							continue;
						}

						String split[] = params.split(" ");

						if (split.length != 2) {
							serverSocket.sendError("Invalid syntax of APOP command.");
							continue;
						}

						String username = split[0];

						if (loggedInUsers.containsKey(username)) {
							serverSocket.sendError("User " + username + " is already logged in.");
							continue;
						}

						try {
							User user = dao.selectUser(username);
							if (user == null) {
								serverSocket.sendError("Unknown user " + username + ".");
								continue;
							}

							String expectedHash = DigestUtils.md5Hex(hashString + user.password);
							String actualHash = split[1];
							if (!actualHash.equals(expectedHash)) {
								serverSocket.sendError("Wrong hash for " + user.username + ", try again.");
								continue;
							}

							popEmails = dao.selectEmailsForPOP(user);

							serverSocket.sendSuccess("Authentication successful.");
							authenticated = true;
							currentUser = user;
							loggedInUsers.put(user.username, user);
						} catch (SQLException e) {
							serverSocket.sendError("Server database error, sorry.");
							logger.log(Level.SEVERE, "Problem authenticating POP3 client.", e);
						}
					} else if ("USER".equals(cmd)) {
						//RFC 1939, p.13
						if (authenticated) {
							serverSocket.sendError("Already authenticated.");
							continue;
						}

						String username = params;
						if (username == null) {
							serverSocket.sendError("Please include the username as an argument to the USER command.");
							continue;
						}

						if (loggedInUsers.containsKey(username)) {
							serverSocket.sendError("User " + username + " is already logged in.");
							continue;
						}

						try {
							User user = dao.selectUser(username);
							if (user == null) {
								serverSocket.sendError("Unknown user " + username + ".");
								continue;
							}

							serverSocket.sendSuccess("User " + username + " found.");
							currentUser = user;
						} catch (SQLException e) {
							serverSocket.sendError("Server database error, sorry");
							logger.log(Level.SEVERE, "Problem authenticating POP3 client.", e);
						}
					} else if ("PASS".equals(cmd)) {
						//RFC 1939, p.14
						if (authenticated) {
							serverSocket.sendError("Already authenticated.");
							continue;
						}

						if (currentUser == null) {
							serverSocket.sendError("Please specify a USER first.");
							continue;
						}

						String password = params;
						if (password == null) {
							serverSocket.sendError("Please include the password as an argument to the PASS command.");
							continue;
						}

						if (!password.equals(currentUser.password)) {
							serverSocket.sendError("Wrong password for " + currentUser.username + ", try again.");
							continue;
						}

						try {
							popEmails = dao.selectEmailsForPOP(currentUser);
						} catch (SQLException e) {
							serverSocket.sendError("Database error, sorry.");
							logger.log(Level.SEVERE, "Problem getting emails for POP from database.", e);
							continue;
						}

						serverSocket.sendSuccess("Authentication successful.");
						authenticated = true;
						loggedInUsers.put(currentUser.username, currentUser);
					} else if ("STAT".equals(cmd)) {
						//RFC 1939, p.6
						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						long totalEmails = 0;
						long totalSize = 0;
						for (POPEmail email : popEmails) {
							if (!email.isDeleted()) {
								totalSize += email.getSize();
								totalEmails++;
							}
						}
						serverSocket.sendSuccess(totalEmails + " " + totalSize);
					} else if ("LIST".equals(cmd)) {
						//RFC 1939, p.6
						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						if (params == null) {
							List<String> msgs = new ArrayList<String>();

							long totalSize = 0;
							long totalEmails = 0;
							for (int i = 0; i < popEmails.size(); i++) {
								POPEmail email = popEmails.get(i);
								if (!email.isDeleted()) {
									totalSize += email.getSize();
									totalEmails++;
									msgs.add(email.getPopId() + " " + email.getSize());
								}
							}
							msgs.add(0, totalEmails + " messages (" + totalSize + " octets)");

							serverSocket.sendSuccess(msgs);
						} else {
							POPEmail email = getPOPEmail(params);
							if (email == null) {
								continue;
							}

							if (email.isDeleted()) {
								serverSocket.sendError("Message is marked for deletion.");
								continue;
							}

							serverSocket.sendSuccess(email.getPopId() + " " + email.getSize());
						}
					} else if ("UIDL".equals(cmd)) {
						//RFC 1939, p.12
						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						if (params == null) {
							List<String> msgs = new ArrayList<String>();

							msgs.add(""); //first line is empty

							for (POPEmail email : popEmails) {
								if (!email.isDeleted()) {
									msgs.add(email.getPopId() + " " + email.getDbId());
								}
							}

							serverSocket.sendSuccess(msgs);
						} else {
							POPEmail email = getPOPEmail(params);
							if (email == null) {
								continue;
							}

							if (email.isDeleted()) {
								serverSocket.sendError("Message is marked for deletion.");
								continue;
							}

							serverSocket.sendSuccess(email.getPopId() + " " + email.getDbId());
						}
					} else if ("RETR".equals(cmd)) {
						//RFC 1939, p.8

						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						String popId = params;
						if (popId == null) {
							serverSocket.sendError("Message ID must be supplied.");
							continue;
						}

						POPEmail email = getPOPEmail(popId);
						if (email == null) {
							continue;
						}

						if (email.isDeleted()) {
							serverSocket.sendError("Message is marked for deletion.");
							continue;
						}

						try {
							String data = dao.selectEmailData(email.getDbId());
							serverSocket.sendSuccess(email.getSize() + " octets" + CRLF + data);
						} catch (SQLException e) {
							serverSocket.sendError("Database error, sorry.");
							logger.log(Level.SEVERE, "Database error getting email body.", e);
						}
					} else if ("TOP".equals(cmd)) {
						//RFC 1939, p.11
						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						if (params == null) {
							serverSocket.sendError("Wrong syntax for TOP.");
							continue;
						}

						String split[] = params.split(" ");
						if (split.length != 2) {
							serverSocket.sendError("Wrong syntax for TOP.");
							continue;
						}

						String popId = split[0];
						int lines;
						try {
							lines = Integer.parseInt(split[1]);
							if (lines < 0) {
								throw new NumberFormatException();
							}
						} catch (NumberFormatException e) {
							serverSocket.sendError("Number of lines must be a positive integer.");
							continue;
						}

						POPEmail email = getPOPEmail(popId);
						if (email == null) {
							continue;
						}

						if (email.isDeleted()) {
							serverSocket.sendError("Message is marked for deletion.");
							continue;
						}

						try {
							EmailData data = new EmailData(dao.selectEmailData(email.getDbId()));
							String dataStr = data.toData();
							Scanner scanner = new Scanner(dataStr);
							StringBuilder response = new StringBuilder(CRLF);
							boolean inBody = false;
							int curBodyLine = 0;
							while (scanner.hasNextLine() && (!inBody || curBodyLine < lines)){
								String line = scanner.nextLine();
								if (line.isEmpty()){
									//an empty line separates the headers from the body
									inBody = true;
								} else {
									if (inBody){
										curBodyLine++;
									}
								}
								
								response.append(line);
								response.append(CRLF);
							}
							
							serverSocket.sendSuccess(response.toString());
						} catch (SQLException e) {
							serverSocket.sendError("Database error, sorry.");
							logger.log(Level.SEVERE, "Database error getting email body.", e);
						}
					} else if ("DELE".equals(cmd)) {
						//RFC 1939, p.8
						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						//mark email for deletion

						POPEmail email = getPOPEmail(params);
						if (email == null) {
							continue;
						}

						if (email.isDeleted()) {
							serverSocket.sendError("Message is marked for deletion.");
							continue;
						}

						email.setDeleted(true);
						serverSocket.sendSuccess("message " + params + " marked for deletion.");
					} else if ("NOOP".equals(cmd)) {
						//RFC 1939, p.9
						serverSocket.sendSuccess();
					} else if ("RSET".equals(cmd)) {
						//RFC 1939, p.9
						if (!authenticated) {
							serverSocket.sendError("Please authenticate first.");
							continue;
						}

						//unmark all emails that are marked for deletion
						for (POPEmail email : popEmails) {
							email.setDeleted(false);
						}
						serverSocket.sendSuccess();
					} else if ("QUIT".equals(cmd)) {
						//RFC 1939, p.5,10
						if (popEmails == null) {
							serverSocket.sendSuccess("Ok, bye.");
						} else {
							//get the database IDs of the emails marked for deletion
							List<Integer> ids = new ArrayList<Integer>();
							for (POPEmail email : popEmails) {
								if (email.isDeleted()) {
									ids.add(email.getDbId());
								}
							}

							//delete emails from database
							if (!ids.isEmpty()) {
								synchronized (dao) {
									try {
										dao.deleteInboxEmails(currentUser, ids);
										dao.commit();
									} catch (Exception e) {
										serverSocket.sendError("Database error, sorry.");
										logger.log(Level.SEVERE, "Error deleting emails via POP3 connection.", e);

										dao.rollback();
										continue;
									}
								}
							}

							serverSocket.sendSuccess("Ok, bye (" + ids.size() + " emails deleted).");
						}

						break;
					} else {
						serverSocket.sendError("Unknown command " + cmd);
					}
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					logger.log(Level.WARNING, "Problem closing socket.", e);
				}

				if (currentUser != null) {
					loggedInUsers.remove(currentUser.username);
				}

				//write transaction log to file
				if (transactionLogFile != null) {
					synchronized (transactionLogFile) {
						try {
							serverSocket.getTransactionLog().writeToFile(transactionLogFile);
						} catch (IOException e) {
							logger.log(Level.WARNING, "Problem writing to transaction log file.", e);
						}
					}
				}
				
				logger.info("POP3 connection with " + socket.getInetAddress().getHostAddress() + " terminated.");
			}
		}

		/**
		 * Gets info on an email, sending an error response if there's a problem
		 * with the input supplied by the client.
		 * @param popIdStr the POP email ID (client input)
		 * @return the email or null if there was an error with the input
		 * @throws IOException
		 */
		private POPEmail getPOPEmail(String popIdStr) throws IOException {
			int popId = 0;
			try {
				popId = Integer.parseInt(popIdStr);
				if (popId <= 0) {
					throw new NumberFormatException();
				} else if (popId > popEmails.size()) {
					serverSocket.sendError("No such message.");
					return null;
				}
			} catch (NumberFormatException e) {
				serverSocket.sendError("Invalid email ID " + popIdStr + ".");
				return null;
			}

			POPEmail email = popEmails.get(popId - 1);
			return email;
		}
	}
}
