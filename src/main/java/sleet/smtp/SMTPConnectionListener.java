package sleet.smtp;

import static sleet.email.EmailRaw.CRLF;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sleet.Sleet;
import sleet.TransactionLog;
import sleet.db.DbDao;
import sleet.db.MailingList;
import sleet.db.MailingListAddress;
import sleet.db.User;
import sleet.email.EmailAddress;
import sleet.email.EmailData;
import sleet.email.EmailDateFormat;
import sleet.email.EmailRaw;

/**
 * Listens for and handles SMTP client connections. Forks each client/server
 * connection into its own thread.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPConnectionListener {
	private static final Logger logger = Logger.getLogger(SMTPConnectionListener.class.getName());

	/**
	 * The database DAO.
	 */
	private final DbDao dao;

	/**
	 * The host name of this server.
	 */
	private String hostName;

	/**
	 * The port to listen for SMTP connections.
	 */
	private int port = 25;

	/**
	 * True if the listener has started, false if not.
	 */
	private boolean started = false;

	/**
	 * All client/server communication is logged here for debugging purposes
	 * (null to not log anything).
	 */
	private File transactionLogFile;

	/**
	 * @param dao the database DAO
	 */
	public SMTPConnectionListener(DbDao dao) {
		this.dao = dao;
	}

	/**
	 * Sets the host name of the server.
	 * @param hostName the host name
	 * @throws IllegalStateException if the listener has already been started
	 * @throws IllegalArgumentException if hostName is null
	 */
	public void setHostName(String hostName) {
		if (started) {
			throw new IllegalStateException("Server properties cannot be changed once the server starts.");
		}
		if (hostName == null) {
			throw new IllegalArgumentException("Host name cannot be null.");
		}
		this.hostName = hostName;
	}

	/**
	 * Sets the port of the server.
	 * @param port the port (defaults to 25)
	 * @throws IllegalStateException if the listener has already been started
	 * @throws IllegalArgumentException if port is not a positive integer
	 */
	public void setPort(int port) {
		if (started) {
			throw new IllegalStateException("Server properties cannot be changed once the server starts.");
		}
		if (port < 1) {
			throw new IllegalArgumentException("Port must be a postive integer.");
		}
		this.port = port;
	}

	/**
	 * Sets the file that all client/server communication will be logged to.
	 * @param transactionLogFile the file or null not to log anything (default)
	 * @throws IllegalStateException if the listener has already been started
	 */
	public void setTransactionLogFile(File transactionLogFile) {
		if (started) {
			throw new IllegalStateException("Transaction log file cannot be changed once the server starts.");
		}
		this.transactionLogFile = transactionLogFile;
	}

	/**
	 * Starts the SMTP server.
	 * @throws IOException
	 * @throws IllegalStateException if the server has not been configured
	 * properly and cannot start
	 */
	public void start() throws IOException {
		if (hostName == null) {
			throw new IllegalStateException("Host name must be set.");
		}

		started = true;

		ServerSocket serverSocket = new ServerSocket(port);
		logger.info("Ready to receive SMTP requests on port " + port + "...");

		while (true) {
			Socket socket = serverSocket.accept();
			logger.info("SMTP connection established with " + socket.getInetAddress().getHostAddress());
			SMTPClientThread thread = new SMTPClientThread(socket);
			thread.start();
		}
	}

	/**
	 * Handles a single client connection.
	 * @author Mike Angstadt [mike.angstadt@gmail.com]
	 */
	private class SMTPClientThread extends Thread {
		private final Socket socket;

		public SMTPClientThread(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			TransactionLog transactionLog = new TransactionLog();
			try {
				Pattern fromPattern = Pattern.compile("FROM:<(.*?)>", Pattern.CASE_INSENSITIVE);
				Pattern toPattern = Pattern.compile("TO:<(.*?)>", Pattern.CASE_INSENSITIVE);

				//open socket in/out streams
				ClientReader clientReader = new ClientReader(socket.getInputStream(), transactionLog);
				ClientWriter clientWriter = new ClientWriter(socket.getOutputStream(), transactionLog);

				boolean ehloSent = false;
				String remoteHostName = null;
				EmailRaw email = null;
				clientWriter.send(220, hostName + " " + Sleet.appName + " v" + Sleet.version + " Ready to receive mail.");
				SMTPRequest clientMsg;
				while ((clientMsg = clientReader.next()) != null) {
					ClientCommand cmd;
					try {
						cmd = ClientCommand.valueOf(clientMsg.getCommand());
					} catch (IllegalArgumentException e) {
						cmd = null;
					}
					String params = clientMsg.getParameters();

					if (cmd == ClientCommand.HELP) {
						List<String> msgs = new ArrayList<String>();
						msgs.add("List of available commands:");
						if (email == null) {
							if (ehloSent) {
								msgs.add("MAIL FROM:<sender address> - The sender of the email.");
							} else {
								msgs.add("EHLO <client host name> - Call this to start sending emails.");
							}
						} else {
							msgs.add("RCPT TO:<recipient address> - A recipient of the email.");
							if (!email.getRecipients().isEmpty()) {
								msgs.add("DATA - Start sending the email message (end with <CRLF>.<CRLF>).");
							}
							msgs.add("RSET - Cancel this email and start over.");
						}
						msgs.add("VRFY <email address> - Check to see if this address exists on this server.");
						msgs.add("EXPN <mailing list address> - Get the recipients of a mailing list.");
						msgs.add("NOOP - Does nothing (always returns 250 Ok response)");
						msgs.add("HELP - Display list of available commands (varies depending on context).");
						msgs.add("QUIT - End this SMTP session.");
						clientWriter.send(214, msgs);
					} else if (cmd == ClientCommand.HELO) {
						remoteHostName = params;
						clientWriter.send(250, "Hello " + params);
						ehloSent = true;
						email = null;
					} else if (cmd == ClientCommand.EHLO) {
						remoteHostName = params;
						ehloSent = true;
						List<String> messages = new ArrayList<String>();
						messages.add(hostName + " Hello" + (params == null ? "" : " " + params));
						messages.add("SIZE 1000000");
						messages.add("HELP");
						//messages.add("EXPN");
						//TODO RFC-5321 p.25 - say that EXPN is supported
						clientWriter.send(250, messages);
						email = null;
					} else if (cmd == ClientCommand.RSET) {
						if (params != null) {
							//this command does not have parameters (see RFC 5321 p.55)
							clientWriter.send(501, "RSET command has no parameters.");
							continue;
						}

						String msg;
						if (email == null) {
							msg = "Ok";
						} else {
							email = null;
							msg = "Ok, email transaction aborted.";
						}
						clientWriter.send(250, msg);
					} else if (cmd == ClientCommand.NOOP) {
						clientWriter.send(250, "Ok");
					} else if (cmd == ClientCommand.VRFY) {
						//RFC-5321, p.14: the address "postmaster" is valid, even though it does not have a host associated with it
						if ("postmaster".equalsIgnoreCase(params)) {
							//append host name
							params = "postmaster@" + hostName;
						}

						EmailAddress addr = new EmailAddress(params);
						if (addr.isValid()) {
							//if an email address was given, then check the host and then see if the user exists

							//check host
							String host = addr.getHost();
							if (!hostName.equalsIgnoreCase(host)) {
								clientWriter.send(551, "Invalid host name: " + addr);
								continue;
							}

							//check to see if username exists
							String username = addr.getMailbox();
							User user = dao.selectUser(username);
							if (user == null) {
								clientWriter.send(550, "User not found with given address: " + addr);
							} else {
								String msg;
								if (user.fullName == null) {
									msg = user.username + "@" + hostName;
								} else {
									msg = user.fullName + " <" + user.username + "@" + hostName + ">";
								}
								clientWriter.send(250, msg);
							}
						} else {
							//search usernames and full names of users

							List<User> users = dao.findUsers(params);
							if (users.isEmpty()) {
								clientWriter.send(550, "No users found: " + params);
							} else {
								List<String> msgs = new ArrayList<String>();
								msgs.add("User ambiguous.  Possibilities are:");
								for (User user : users) {
									String msg = "";
									if (user.fullName != null) {
										msg += user.fullName + " ";
									}
									msg += "<" + user.username + "@" + hostName + ">";
									msgs.add(msg);
								}

								//always use "ambiguous" status code because it's not clear whether the client is searching for a username or the person's real name
								clientWriter.send(553, msgs);
							}
						}
					} else if (cmd == ClientCommand.EXPN) {
						String name = params;
						if (name.isEmpty()) {
							clientWriter.send(501, "No mailing list specified.");
							continue;
						}

						MailingList mailingList = dao.selectMailingList(name);
						if (mailingList == null || mailingList.addresses.isEmpty()) {
							clientWriter.send(550, "Mailing list doesn't exist: " + name);
						} else {
							List<String> lines = new ArrayList<String>();
							for (MailingListAddress address : mailingList.addresses) {
								String msg;
								if (address.name == null) {
									msg = address.address;
								} else {
									msg = address.name + " <" + address.address + ">";
								}
								lines.add(msg);
							}
							clientWriter.send(250, lines);
						}
					} else if (cmd == ClientCommand.MAIL) {
						if (!ehloSent) {
							clientWriter.send(503, "EHLO required before emails can be received.");
							continue;
						}

						//get "from" address
						Matcher m = fromPattern.matcher(params);
						if (m.find()) {
							if (email != null && email.getMailFrom() != null) {
								clientWriter.send(503, "\"From\" address already specified.  Use RCPT to define recipients and DATA to define the message body.");
								continue;
							}

							String addrStr = m.group(1);
							EmailAddress addr = new EmailAddress(addrStr);
							if (!addr.isValid()) {
								clientWriter.send(501, "Invalid syntax of email address: " + addrStr);
								continue;
							}

							email = new EmailRaw();
							email.setMailFrom(addr);
							clientWriter.send(250, "Ok");
						} else {
							clientWriter.send(501, "MAIL command must look like: \"MAIL FROM:<mailbox@host>\"");
						}
					} else if (cmd == ClientCommand.RCPT) {
						if (email == null) {
							clientWriter.send(503, "MAIL command must be used before RCPT can be used");
							continue;
						}

						Matcher m = toPattern.matcher(params);
						if (m.find()) {
							String addrStr = m.group(1);

							//RFC-5321, p.14: the address "postmaster" is valid, even though it does not have a host associated with it
							if ("postmaster".equalsIgnoreCase(addrStr)) {
								//append host name
								addrStr = "postmaster@" + hostName;
							}

							EmailAddress addr = new EmailAddress(addrStr);

							//check syntax
							if (!addr.isValid()) {
								clientWriter.send(501, "Invalid syntax of email address: " + addrStr);
								continue;
							}

							//check host name
							String host = addr.getHost();
							if (!hostName.equalsIgnoreCase(host)) {
								clientWriter.send(551, "Invalid host name: " + host);
								continue;
							}

							//check mailbox
							String mailbox = addr.getMailbox();
							if (!dao.doesMailboxExist(mailbox)) {
								clientWriter.send(550, "Mailbox not found: " + mailbox);
								continue;
							}

							email.addRecipient(addr);
							clientWriter.send(250, "Ok");
						} else {
							//invalid syntax
							clientWriter.send(501, "RCPT command must look like: \"RCPT TO:<mailbox@" + hostName + ">\"");
							continue;
						}
					} else if (cmd == ClientCommand.DATA) {
						if (email == null) {
							clientWriter.send(503, "MAIL command must be used before DATA can be sent.");
							continue;
						}

						if (params != null) {
							//this command does not have parameters (see RFC 5321 p.55)
							clientWriter.send(501, "DATA command has no parameters.");
							continue;
						}

						if (email.getRecipients().isEmpty()) {
							clientWriter.send(503, "At least one RCPT (recipient) is required before DATA can be sent.");
							continue;
						}

						//get mail message body
						clientWriter.send(354, "Ready.");

						StringBuilder data = new StringBuilder();
						String dataLine;
						while ((dataLine = clientReader.nextDataLine()) != null) {
							data.append(dataLine).append(CRLF);
						}
						email.setData(new EmailData(data.toString()));

						DateFormat df = new EmailDateFormat();

						//trace info--from clause
						//existing "Received" headers in the email must not be modified or removed
						// see RFC 5321 p.57
						String remoteIp = socket.getInetAddress().getHostAddress();
						StringBuilder sb = new StringBuilder();
						sb.append("from ");
						if (remoteHostName == null) {
							sb.append(remoteIp);
						} else {
							sb.append(remoteHostName + " ([" + remoteIp + "])");
						}
						sb.append(" by " + hostName + "; " + df.format(new Date()));
						email.getData().getHeaders().addHeader("Received", sb.toString());
						email.getData().getHeaders().addHeader("Return-Path", "<" + email.getMailFrom().getAddress() + ">");

						//add mail message to database
						Exception error = null;
						sleet.db.Email dbEmail = null;
						synchronized (dao) {
							try {
								dbEmail = new sleet.db.Email();
								dbEmail.sender = email.getMailFrom();
								dbEmail.recipients = email.getRecipients();
								dbEmail.data = email.getData();
								dao.insertInboxEmail(dbEmail);
								dao.commit();
							} catch (Exception e) {
								dao.rollback();

								error = e;
								logger.log(Level.SEVERE, "Error saving email to database.", e);
							}
						}

						//TODO send emails to any mailing lists

						if (error == null) {
							clientWriter.send(250, "Ok: queued as " + dbEmail.id);
						} else {
							clientWriter.send(451, "An unexpected server error occurred while saving the email, sorry: " + error.getMessage());
						}
						email = null;
					} else if (cmd == ClientCommand.QUIT) {
						if (params != null) {
							//this command does not have parameters (see RFC 5321 p.55)
							clientWriter.send(501, "QUIT command has no parameters.");
							continue;
						}

						String msg;
						if (email == null) {
							msg = "Bye";
						} else {
							msg = "Email transaction aborted.  Bye";
						}
						clientWriter.send(221, msg);
						break;
					} else {
						clientWriter.send(500, "Unknown command: " + clientMsg.getCommand());
					}
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "SMTP error.", e);
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
							transactionLog.writeToFile(transactionLogFile);
						} catch (IOException e) {
							logger.log(Level.WARNING, "Problem writing to transaction log file.", e);
						}
					}
				}

				logger.info("SMTP connection with " + socket.getInetAddress().getHostAddress() + " terminated.");
			}
		}
	}
}