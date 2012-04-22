package sleet;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import sleet.admin.AdminConnectionListener;
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

			System.out.println("--smtp-port=PORT");
			System.out.println("The SMTP server port (defaults to 25).");
			System.out.println();

			System.out.println("--smtp-msa-port=PORT");
			System.out.println("The SMTP mail submission port (defaults to 587).");
			System.out.println();

			System.out.println("--pop3-port=PORT");
			System.out.println("The POP3 server port (defaults to 110).");
			System.out.println();
			
			System.out.println("--admin-port=PORT");
			System.out.println("The Sleet admin console port (defaults to 2553).");
			System.out.println();

			System.out.println("--host-name=NAME [required]");
			System.out.println("The host name of this server (e.g. myserver.com).");
			System.out.println("This is what's used in email addresses destined for and coming from this server.");
			System.out.println();

			System.out.println("--database=PATH");
			System.out.println("The path to where the database will be stored or \"MEM\" to use an in-memory");
			System.out.println("database (defaults to \"sleet-db\").");
			System.out.println();

			System.out.println("--smtp-inbound-log=PATH");
			System.out.println("The path to where inbound SMTP transactions are logged.");
			System.out.println();

			System.out.println("--smtp-outbound-log=PATH");
			System.out.println("The path to where outbound SMTP transactions are logged.");
			System.out.println();
			
			System.out.println("--smtp-msa-log=PATH");
			System.out.println("The path to where inbound SMTP mail submission transactions are logged.");
			System.out.println();

			System.out.println("--pop3-log=PATH");
			System.out.println("The path to where POP3 transactions are logged.");
			System.out.println();
			
			System.out.println("--admin-log=PATH");
			System.out.println("The path to where Sleet admin console transactions are logged.");
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
		Set<String> validArgs = new HashSet<String>(Arrays.asList(new String[] { "smtp-port", "smtp-msa-port", "pop3-port", "admin-port", "host-name", "database", "smtp-inbound-log", "smtp-outbound-log", "smtp-msa-log", "pop3-log", "admin-log", "version", "help" }));
		Collection<String> invalidArgs = arguments.invalidArgs(validArgs);
		if (!invalidArgs.isEmpty()) {
			System.err.println("One or more non-existent arguments were specified:\n" + invalidArgs);
			System.exit(1);
		}

		//host name is required
		final String hostName = arguments.value(null, "host-name");
		if (hostName == null) {
			System.err.println("A host name must be specified.");
			System.exit(1);
		}

		final int smtpPort = arguments.valueInt(null, "smtp-port", 25);
		final int smtpMsaPort = arguments.valueInt(null, "smtp-msa-port", 587);
		final int popPort = arguments.valueInt(null, "pop3-port", 110);
		final int adminPort = arguments.valueInt(null, "admin-port", 2553);

		String smtpInboundLog = arguments.value(null, "smtp-inbound-log");
		String smtpOutboundLog = arguments.value(null, "smtp-outbound-log");
		String smtpMsaLog = arguments.value(null, "smtp-msa-log");
		String pop3Log = arguments.value(null, "pop3-log");
		String adminLog = arguments.value(null, "admin-log");

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
		if (smtpOutboundLog != null) {
			mailSender.setTransactionLogFile(new File(smtpOutboundLog));
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
		if (pop3Log != null) {
			popServer.setTransactionLogFile(new File(pop3Log));
		}
		Thread popThread = new Thread() {
			@Override
			public void run() {
				try {
					popServer.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "The POP3 server encountered an error.  Server terminated.", e);
					throw new RuntimeException("Cannot start POP3 server on port " + popPort + ".", e);
				}
			}
		};
		popThread.start();

		//start the SMTP server
		final SMTPConnectionListener smtpServer = new SMTPConnectionListener(dao);
		smtpServer.setHostName(hostName);
		smtpServer.setPort(smtpPort);
		if (smtpInboundLog != null) {
			smtpServer.setTransactionLogFile(new File(smtpInboundLog));
		}
		Thread smtpThread = new Thread() {
			@Override
			public void run() {
				try {
					smtpServer.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "The SMTP server encountered an error.  Server terminated.", e);
					throw new RuntimeException("Cannot start SMTP server on port " + smtpPort + ".", e);
				}
			}
		};
		smtpThread.start();
		
		//start the SMTP MSA (mail submission agent) server
		final SMTPConnectionListener smtpMsaServer = new SMTPConnectionListener(dao, mailSender);
		smtpMsaServer.setHostName(hostName);
		smtpMsaServer.setPort(smtpMsaPort);
		if (smtpMsaLog != null) {
			smtpMsaServer.setTransactionLogFile(new File(smtpMsaLog));
		}
		Thread smtpMsaThread = new Thread() {
			@Override
			public void run() {
				try {
					smtpMsaServer.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "The SMTP mail submission server encountered an error.  Server terminated.", e);
					throw new RuntimeException("Cannot start SMTP mail submission server on port " + smtpMsaPort + ".", e);
				}
			}
		};
		smtpMsaThread.start();
		
		//start the admin console
		final AdminConnectionListener adminServer = new AdminConnectionListener(dao);
		adminServer.setHostName(hostName);
		adminServer.setPort(adminPort);
		if (adminLog != null) {
			adminServer.setTransactionLogFile(new File(adminLog));
		}
		Thread adminThread = new Thread() {
			@Override
			public void run() {
				try {
					adminServer.start();
				} catch (Exception e) {
					logger.log(Level.SEVERE, "The Sleet admin console encountered an error.  Server terminated.", e);
					throw new RuntimeException("Cannot start Sleet admin console on port " + adminPort + ".", e);
				}
			}
		};
		adminThread.start();
	}
}
