package sleet.admin;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import sleet.db.DbDao;
import sleet.db.User;

/**
 * Listens for and handles admin console client connections. Forks each
 * client/server connection into its own thread.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class AdminConnectionListener {
	private static final Logger logger = Logger.getLogger(AdminConnectionListener.class.getName());
	private final DbDao dao;

	/**
	 * True if the listener has started, false if not.
	 */
	private boolean started = false;

	/**
	 * The port that it listens on.
	 */
	private int port = 2553;

	/**
	 * The host name of this email server.
	 */
	private String hostName;

	/**
	 * All client/server communication is logged here for debugging purposes
	 * (null to not log anything).
	 */
	private File transactionLogFile;

	/**
	 * @param dao the database DAO
	 */
	public AdminConnectionListener(DbDao dao) {
		this.dao = dao;
	}

	/**
	 * Sets the port to listen on.
	 * @param port the port (defaults to 2553)
	 * @throws IllegalStateException if the listener has already been started
	 * @throws IllegalArgumentException if port is not a positive integer
	 */
	public void setPort(int port) {
		if (started) {
			throw new IllegalStateException("Sleet admin console properties cannot be changed once the server starts.");
		}
		if (port < 1) {
			throw new IllegalArgumentException("Port must be a postive integer.");
		}
		this.port = port;
	}

	/**
	 * Sets the host name of the server.
	 * @param hostName the host name
	 * @throws IllegalStateException if the listener has already been started
	 * @throws IllegalArgumentException if hostName is null
	 */
	public void setHostName(String hostName) {
		if (started) {
			throw new IllegalStateException("Sleet admin console properties cannot be changed once the server starts.");
		}
		if (hostName == null) {
			throw new IllegalArgumentException("Host name cannot be null.");
		}
		this.hostName = hostName;
	}

	/**
	 * Sets the file that all client/server communication will be logged to.
	 * @param transactionLogFile the file or null not to log anything (default)
	 * @throws IllegalStateException if the listener has already been started
	 */
	public void setTransactionLogFile(File transactionLogFile) {
		if (started) {
			throw new IllegalStateException("Sleet admin console properties cannot be changed once the server starts.");
		}
		this.transactionLogFile = transactionLogFile;
	}

	/**
	 * Starts the listener.
	 * @throws IOException
	 * @throws IllegalStateException if the listener has not been configured
	 * properly and cannot start
	 */
	public void start() throws IOException {
		if (hostName == null) {
			throw new IllegalStateException("Host name must be set.");
		}

		started = true;

		ServerSocket serverSocket = new ServerSocket(port);
		logger.info("Ready to receive Sleet admin console requests on port " + port + "...");

		while (true) {
			Socket socket = serverSocket.accept();
			logger.info("Sleet admin console connection established with " + socket.getInetAddress().getHostAddress());
			AdminConversation thread = new AdminConversation(socket);
			thread.start();
		}
	}

	/**
	 * Handles a single client connection.
	 * @author Mike Angstadt [mike.angstadt@gmail.com]
	 */
	private class AdminConversation extends Thread {
		private final Socket socket;
		private final AdminServerSocket serverSocket;

		public AdminConversation(Socket socket) throws IOException {
			this.socket = socket;
			this.serverSocket = new AdminServerSocket(socket.getInputStream(), socket.getOutputStream());
		}

		@Override
		public void run() {
			boolean shutdown = false;
			try {
				//send welcome message
				serverSocket.sendSuccess("Sleet admin console.");

				AdminRequest request;
				while ((request = serverSocket.nextRequest()) != null) {
					String cmd = request.getCommand();
					Map<String, String> params = request.getParameters();

					if ("HELP".equals(cmd)){
						StringWriter sw = new StringWriter();
						PrintWriter writer = new PrintWriter(sw);
						writer.println("Help message is as follows:");
						writer.println("Each command consists of the command name, followed by parameters.");
						writer.println("Parameters are space-delimited and take the form of: NAME=VALUE");
						writer.println("If VALUE has spaces in it, then it can be surrounded with double quotes.");
						writer.println();
						writer.println("Example: CREATE_USER username=fred password=secret fullname=\"Fred Flintstone\"");
						writer.println();
						writer.println("LIST_USERS");
						writer.println("  Description: Displays all existing user accounts.");
						writer.println("  Parameters:  none");
						writer.println("CREATE_USER");
						writer.println("  Description: Creates a new user account.");
						writer.println("  Parameters:");
						writer.println("     username     the username of the account.");
						writer.println("     password     the password of the account.");
						writer.println("     fullname     (optional) the user's full name.");
						writer.println("MODIFY_USER");
						writer.println("  Description: Modifies an existing user account.");
						writer.println("  Parameters:");
						writer.println("     username     the current username of the account.");
						writer.println("     newusername  (optional) the account's new username.");
						writer.println("     newpassword  (optional) the account's new password.");
						writer.println("     newfullname  (optional) the account's new full name.");
						writer.println("DELETE_USER");
						writer.println("  Description: Deletes an existing user account.");
						writer.println("  Parameters:");
						writer.println("     username     the username of the account to delete.");
						writer.println("SHUTDOWN");
						writer.println("  Description: Shuts down the Sleet SMTP server.");
						writer.println("  Parameters:  none");
						writer.println("QUIT");
						writer.println("  Description: Exits the admin console.");
						writer.println("  Parameters:  none");
						writer.println("HELP");
						writer.println("  Description: Prints this help message.");
						writer.println("  Parameters:  none");
						serverSocket.sendSuccess(sw.toString());
					}
					else if ("LIST_USERS".equals(cmd)) {
						try {
							List<User> users = dao.selectUsers();
							List<String> lines = new ArrayList<String>();
							lines.add(users.size() + " user(s):");
							for (User user : users) {
								StringBuilder sb = new StringBuilder();
								sb.append("username=\"").append(user.username).append("\" ");
								sb.append("fullname=\"").append(user.fullName).append("\"");
								lines.add(sb.toString());
							}
							serverSocket.sendSuccess(lines);
						} catch (Exception e) {
							logger.log(Level.SEVERE, "Error listing users.", e);
							serverSocket.sendError("Error listing users: " + e.getMessage());
						}
					} else if ("CREATE_USER".equals(cmd)) {
						//TODO validation
						String username = params.get("username");
						String password = params.get("password");
						String fullName = params.get("fullname");

						try {
							//make sure user doesn't already exist
							if (dao.doesMailboxExist(username)) {
								serverSocket.sendError("User \"" + username + "\" already exists.");
								continue;
							}

							User user = new User();
							user.username = username;
							user.password = password;
							user.fullName = fullName;

							synchronized (dao) {
								try {
									dao.insertUser(user);
									dao.commit();
									serverSocket.sendSuccess("User \"" + username + "\" created.");
								} catch (Exception e) {
									dao.rollback();
									throw e;
								}
							}
						} catch (Exception e) {
							logger.log(Level.SEVERE, "Error creating user.", e);
							serverSocket.sendError("Error creating user: " + e.getMessage());
						}
					} else if ("MODIFY_USER".equals(cmd)) {
						String username = params.get("username");
						String newUsername = params.get("newusername");
						String newPassword = params.get("newpassword");
						String newFullName = params.get("newfullname");

						try {
							//make sure user doesn't already exist
							User user = dao.selectUser(username);
							if (user == null) {
								serverSocket.sendError("User \"" + username + "\" does not exist.");
								continue;
							}

							if (newUsername != null) {
								user.username = newUsername;
							}
							if (newPassword != null) {
								user.password = newPassword;
							}
							if (newFullName != null) {
								user.fullName = newFullName;
							}

							synchronized (dao) {
								try {
									dao.updateUser(user);
									dao.commit();
									serverSocket.sendSuccess("User \"" + (newUsername == null ? username : newUsername) + "\" updated.");
								} catch (Exception e) {
									dao.rollback();
									throw e;
								}

							}
						} catch (Exception e) {
							logger.log(Level.SEVERE, "Error modifying user.", e);
							serverSocket.sendError("Error modifying user: " + e.getMessage());
						}
					} else if ("DELETE_USER".equals(cmd)) {
						String username = params.get("username");

						try {
							if (!dao.doesMailboxExist(username)) {
								serverSocket.sendError("User \"" + username + "\" does not exist.");
								continue;
							}

							synchronized (dao) {
								try {
									dao.deleteUser(username);
									dao.commit();
									serverSocket.sendSuccess("User \"" + username + "\" deleted.");
								} catch (Exception e) {
									dao.rollback();
									throw e;
								}
							}
						} catch (Exception e) {
							logger.log(Level.SEVERE, "Error deleting user.", e);
							serverSocket.sendError("Error deleting user: " + e.getMessage());
						}
					} else if ("SHUTDOWN".equals(cmd)) {
						serverSocket.sendSuccess("Shutting down.");
						shutdown = true;
						break;
					} else if ("QUIT".equals(cmd)) {
						serverSocket.sendSuccess("Bye.");
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

				logger.info("Sleet admin console connection with " + socket.getInetAddress().getHostAddress() + " terminated.");

				if (shutdown) {
					System.exit(0);
				}
			}
		}
	}
}
