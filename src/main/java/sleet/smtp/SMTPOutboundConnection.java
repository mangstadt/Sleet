package sleet.smtp;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import sleet.TransactionLog;
import sleet.email.Email;
import sleet.email.EmailAddress;

//TODO separate mailbox and host names in email addresses
//TODO create new socket for each host name in the "to" field of the email
//TODO queue messages that fail and keep trying to send them for ~1 day
//TODO implement timeout for each client command (p.65)
//p.67 "Experience suggests that failures are typically transient (the target system or its connection has crashed), favoring a policy of two connection attempts in the first hour the message is in the queue, and then backing off to one every two or three hours."
//bookmark: p.67
/*
 * The server sends the client a list of its extensions as a response to a EHLO command (HELO is deprecated, but should still be supported)
 * EHLO keywords that start with "X" are custom keywords and are not part of any official standards, the same applies to verbs and commands
 * IANA maintains a list of all extensions
 * Mail Transport Agent (MTA) - a SMTP server that sends and receives emails
 * Mail User Agent (MUA) - a mail client that reads emails from and sends emails to the MTA
 * Host names should not be identified by IP addresses?
 * email address format: MAILBOXNAME@HOSTNAME
 * Mailbox name "postmaster" must always be accepted and does not need a host name associated with it.
 * Email envelope must be in US-ASCII character set (though there are extensions that allow for other charsets)
 * 
 * originating system - the system that sends the email
 * delivery system - the system that receives the email
 * relay system - receives an email and then forwards it to a delivery system or another relay system without modification
 * gateway system - forwards an email to another system that uses a different transport protocol (e.g. receives it in TCP and forwards it in FTP or something)
 * 
 */
/**
 * Opens a connection to a remote SMTP server for sending emails to that server.
 * 
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPOutboundConnection {
	private final Socket socket;
	private final SMTPClientSocket client;
	private final String originatingHost;
	private List<String> extensions = new ArrayList<String>();

	public static void main(String args[]) throws Exception {
		SMTPOutboundConnection conn = new SMTPOutboundConnection("mangstadt.dyndns.org", MxRecordResolver.resolveSmtpServers("gmail.com").get(0));
		System.out.println(conn.vrfy("mike.angstadt@gmail.com"));
		
		conn.close();
		System.out.println(conn.getTransactionLog());
	}

	/**
	 * @param originatingHost the name of the originating host
	 * @param remoteHost the address of the SMTP server to connect to
	 * @throws Exception
	 */
	public SMTPOutboundConnection(String originatingHost, String remoteHost) throws SMTPException, IOException {
		this(originatingHost, remoteHost, 25);
	}

	/**
	 * @param originatingHost the name of the originating host
	 * @param remoteHost the address of the SMTP server to connect to
	 * @param port the port to connect to
	 * @throws SMTPException if there was a problem initiating the SMTP
	 * connection
	 * @throws IOException if there was a socket-related problem
	 */
	public SMTPOutboundConnection(String originatingHost, String remoteHost, int port) throws SMTPException, IOException {
		this.originatingHost = originatingHost;

		socket = new Socket(remoteHost, port);
		client = new SMTPClientSocket(socket.getInputStream(), socket.getOutputStream());

		try {
			connect();
		} catch (SMTPException e) {
			try {
				close();
			} catch (Exception e2) {
				//ignore
			}
			throw e;
		} catch (IOException e) {
			try {
				close();
			} catch (Exception e2) {
				//ignore
			}
			throw e;
		}
	}

	/**
	 * Performs the initial SMTP connection commands.
	 * 
	 * @throws SMTPException if an unexpected SMTP message was received
	 * @throws IOException if there was a socket-related problem
	 */
	private void connect() throws SMTPException, IOException {
		SMTPResponse response = client.connect();
		if (response.getStatusCode() == 220) {
			response = client.ehlo(originatingHost);
			if (response.getStatusCode() == 250) {
				extensions.addAll(response.getMessages());
			} else if (response.getStatusCode() == 502) {
				//RPC-5321, p.18: if the server doesn't support "EHLO", send "HELO"
				response = client.helo(originatingHost);
				if (response.getStatusCode() != 250) {
					throw new SMTPException("Unexpected server message: " + response);
				}
			}
		} else if (response.getStatusCode() == 554) {
			//RFC-5321, p.18
			throw new SMTPException(response.toString());
		} else {
			throw new SMTPException("Unexpected server message: " + response);
		}
	}
	
	public SendResult sendEmail(List<EmailAddress> recipients, sleet.db.Email email) throws SMTPException, IOException {
		SendResult sendResult = new SendResult();

		//from field can be blank (see RPC 5321, p.27-8)
		String from = (email.sender == null) ? "" : email.sender.getAddress();
		SMTPResponse response = client.mail(from);

		if (response.getStatusCode() != 250 && response.getStatusCode() != 251 && response.getStatusCode() != 252) {
			throw new SMTPException("Unexpected server message: " + response);
		}

		for (EmailAddress recipient : recipients) {
			response = client.rcpt(recipient.getAddress());
			if (response.getStatusCode() >= 550 && response.getStatusCode() <= 559) {
				//SMTP server didn't like that email address
				sendResult.failedAddresses.add(recipient);
				sendResult.failedAddressesMessages.add(response.toString());
			} else if (response.getStatusCode() != 250){
				throw new SMTPException("Unexpected server message: " + response);
			} else {
				sendResult.successfulAddresses.add(recipient);
			}
		}

		if (sendResult.failedAddresses.size() == recipients.size()) {
			//server didn't like any of the email addresses, so don't sent the email body
			response = client.rset();
		} else {
			response = client.data();
			if (response.getStatusCode() != 354) {
				throw new SMTPException("Unexpected server message: " + response);
			}
			
			response = client.data(email.data.toData());
			if (response.getStatusCode() != 250) {
				throw new SMTPException("Unexpected server message: " + response);
			}
		}
		
		return sendResult;
	}

	/**
	 * Sends an email.
	 * 
	 * @param recipients the email recipients. These are all expected to have a
	 * host name which corresponds to the SMTP host that we're connected to.
	 * Therefore, these emails may only be a subset of the email's entire
	 * recipient list. In the case that the email has recipients with different
	 * hosts, one {@link SMTPOutboundConnection} instance should be created for
	 * each host. For example, if an email is addressed to a "yahoo.com" and a
	 * "gmail.com" address, then one {@link SMTPOutboundConnection} object
	 * should be created for each of these domains.
	 * @param email the email to send
	 * @return the failed recipients
	 * @throws SMTPException if an unexpected SMTP message is encountered
	 * @throws IOException if a socket-related problem occurred
	 */
	public synchronized SendResult sendEmail(List<EmailAddress> recipients, Email email) throws SMTPException, IOException {
		SendResult sendResult = new SendResult();

		//from field can be blank (see RPC 5321, p.27-8)
		String from = (email.getEmailRaw().getMailFrom() == null) ? "" : email.getEmailRaw().getMailFrom().getAddress();
		SMTPResponse response = client.mail(from);

		if (response.getStatusCode() != 250 && response.getStatusCode() != 251 && response.getStatusCode() != 252) {
			throw new SMTPException("Unexpected server message: " + response);
		}

		for (EmailAddress recipient : recipients) {
			response = client.rcpt(recipient.getAddress());
			if (response.getStatusCode() >= 550 && response.getStatusCode() <= 559) {
				//SMTP server didn't like that email address
				sendResult.failedAddresses.add(recipient);
				sendResult.failedAddressesMessages.add(response.toString());
			} else if (response.getStatusCode() != 250){
				throw new SMTPException("Unexpected server message: " + response);
			}
		}

		if (sendResult.failedAddresses.size() == recipients.size()) {
			//server didn't like any of the email addresses, so don't sent the email body
			response = client.rset();
		} else {
			response = client.data();
			if (response.getStatusCode() != 354) {
				throw new SMTPException("Unexpected server message: " + response);
			}
			response = client.data(email.getData().toData());
			if (response.getStatusCode() != 250) {
				throw new SMTPException("Unexpected server message: " + response);
			}
		}
		
		return sendResult;
	}

	/**
	 * Verifies whether the host recognizes an email address or not.
	 * 
	 * @param address the address to verify
	 * @return the response message
	 * @throws SMTPException if an unexpected SMTP message was received
	 * @throws IOException if a socket-related error occurred
	 */
	public synchronized String vrfy(String address) throws SMTPException, IOException {
		SMTPResponse response = client.vrfy(address);
		if (response.getStatusCode() >= 250 || response.getStatusCode() <= 259) {
			return response.getMessage();
		} else if (response.getStatusCode() == 553) {
			//ambiguous
			StringBuilder sb = new StringBuilder();
			for (String message : response.getMessages()) {
				sb.append(message);
				sb.append('\n');
			}
			return sb.toString();
		} else {
			throw new SMTPException("Unexpected server message: " + response);
		}
	}

	/**
	 * Gets the emails that make up a mailing list.
	 * 
	 * @param mailingList the mailing list address
	 * @return the server response
	 * @throws SMTPException if an unexpected SMTP message was received
	 * @throws IOException if a socket-related error occurred
	 */
	public synchronized List<String> expn(String mailingList) throws SMTPException, IOException {
		SMTPResponse response = client.expn(mailingList);
		if (response.getStatusCode() == 250) {
			List<String> emails = new ArrayList<String>();
			for (String message : response.getMessages()) {
				emails.add(message);
			}
			return emails;
		} else {
			throw new SMTPException("Unexpected server message: " + response);
		}
	}
	
	public TransactionLog getTransactionLog(){
		return client.getTransactionLog();
	}

	/**
	 * Closes the SMTP connection.
	 * 
	 * @throws SMTPException if an unexpected SMTP message was received
	 * @throws IOException if a socket-related error occurred
	 */
	public synchronized void close() throws SMTPException, IOException {
		try {
			SMTPResponse response = client.quit();
			if (response.getStatusCode() != 221) {
				throw new SMTPException("Unexpected server message when trying to send QUIT: \"" + response + "\"");
			}
		} finally {
			IOUtils.closeQuietly(socket);
		}
	}
}