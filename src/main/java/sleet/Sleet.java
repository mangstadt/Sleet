package sleet;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import sleet.db.DbDao;
import sleet.db.DirbyEmbeddedDbDao;
import sleet.db.DirbyMemoryDbDao;
import sleet.pop3.POP3ConnectionListener;
import sleet.smtp.MailSender;
import sleet.smtp.SMTPConnectionListener;

/**
 * Entry point into the application.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class Sleet {
	public static final String appName = "Sleet";
	public static final String version = "0.1";

	private static final Logger logger = Logger.getLogger(Sleet.class.getName());

	public static void main(String args[]) throws Exception {
		Arguments arguments = new Arguments(args);
		if (arguments.exists(null, "help")) {
			System.out.println(appName + " v" + version);
			System.out.println("Sleet is an SMTP server with support for the POP3 protocol.");
			System.out.println();

			System.out.println("ARGUMENTS==============");
			System.out.println();

			System.out.println("--smtpPort=PORT");
			System.out.println("The SMTP server port (defaults to 25).");
			System.out.println();

			//System.out.println("--smtpSubmissionPort=PORT");

			System.out.println("--pop3Port=PORT");
			System.out.println("The POP3 server port (defaults to 110).");
			System.out.println();

			System.out.println("--hostName=NAME [required]");
			System.out.println("The external host name of this server (e.g. myserver.com).");
			System.out.println("This is what's used in email addresses coming from this server.");
			System.out.println();

			System.out.println("--database=PATH");
			System.out.println("The path to where the database will be stored or \"MEM\" to use an in-memory");
			System.out.println("database (defaults to \"sleet-db\").");
			System.out.println();

			System.out.println("--smtp-server-log=PATH");
			System.out.println("The path to where SMTP transactions that the server receives are logged.");
			System.out.println();

			System.out.println("--smtp-client-log=PATH");
			System.out.println("The path to where SMTP transactions that the server sends are logged.");
			System.out.println();

			System.out.println("--pop3-log=PATH");
			System.out.println("The path to where POP3 transactions that the server receives are logged.");
			System.out.println();

			System.out.println("--version");
			System.out.println("Prints the version.");
			System.out.println();

			System.out.println("--help");
			System.out.println("Prints this help message.");

			System.exit(0);
		}

		if (arguments.exists(null, "version")) {
			System.out.println(appName + " v" + version);
			System.exit(0);
		}

		//check for non-existant arguments
		Set<String> validArgs = new HashSet<String>(Arrays.asList(new String[] { "smtpPort", "pop3Port", "hostName", "database", "smtp-server-log", "smtp-client-log", "pop3-log", "version", "help" }));
		Collection<String> invalidArgs = arguments.invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			System.err.println("One or more non-existant arguments were specified:\n" + invalidArgs);
			System.exit(1);
		}

		//host name is required
		final String hostName = arguments.value(null, "hostName");
		if (hostName == null) {
			System.err.println("A host name must be specified.");
			System.exit(1);
		}

		final int smtpPort = arguments.valueInt(null, "smtpPort", 25);
		//int smtpSubmissionPort = arguments.valueInt(null, "smtpSubmissionPort", 587);
		final int popPort = arguments.valueInt(null, "pop3Port", 110);

		String receivingTransactionFile = arguments.value(null, "smtp-server-log");
		String sendingTransactionFile = arguments.value(null, "smtp-client-log");
		String pop3TransactionFile = arguments.value(null, "pop3-log");

		//connect to the database
		String dbPath = arguments.value(null, "database", "sleet-db");
		DbDao dao;
		if ("MEM".equals(dbPath)) {
			dao = new DirbyMemoryDbDao();
		} else {
			File databaseDir = new File(dbPath);
			dao = new DirbyEmbeddedDbDao(databaseDir);
		}

		//start the mail sender
		final MailSender mailSender = new MailSender(dao);
		mailSender.setHostName(hostName);
		if (sendingTransactionFile != null) {
			mailSender.setTransactionLogFile(new File(sendingTransactionFile));
		}
		Thread mailSenderThread = new Thread() {
			@Override
			public void run() {
				try {
					mailSender.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "An error occurred with the mail sender.  Mail sender terminated.", e);
					throw new RuntimeException(e);
				}
			}
		};
		mailSenderThread.start();

		//start the POP server
		final POP3ConnectionListener popServer = new POP3ConnectionListener(dao);
		popServer.setHostName(hostName);
		popServer.setPort(popPort);
		if (pop3TransactionFile != null) {
			popServer.setTransactionLogFile(new File(pop3TransactionFile));
		}
		Thread popThread = new Thread() {
			@Override
			public void run() {
				try {
					popServer.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "The POP3 server encountered an error.  POP3 server terminated.", e);
					throw new RuntimeException("Cannot start POP3 server on port " + popPort + ".", e);
				}
			}
		};
		popThread.start();

		//start the SMTP server
		final SMTPConnectionListener smtpServer = new SMTPConnectionListener(dao);
		smtpServer.setHostName(hostName);
		smtpServer.setPort(smtpPort);
		if (receivingTransactionFile != null) {
			smtpServer.setTransactionLogFile(new File(receivingTransactionFile));
		}
		Thread smtpThread = new Thread() {
			@Override
			public void run() {
				try {
					smtpServer.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "The SMTP server encountered an error.  SMTP server terminated.", e);
					throw new RuntimeException("Cannot start SMTP server on port " + smtpPort + ".", e);
				}
			}
		};
		smtpThread.start();
	}
}
