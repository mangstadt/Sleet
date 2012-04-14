package sleet.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an email in the form that it's sent in over the wire with SMTP.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class EmailRaw {
	/**
	 * The newline string used in emails.
	 */
	public static final String CRLF = "\r\n";

	/**
	 * The "from" address ("MAIL" command).
	 */
	private EmailAddress mailFrom;

	/**
	 * The "to" address(es) ("RCPT" command).
	 */
	private List<EmailAddress> recipients = new ArrayList<EmailAddress>();

	/**
	 * The data portion of the email ("DATA" command).
	 */
	private EmailData data = new EmailData();

	/**
	 * Gets the "from" address ("MAIL" command).
	 * @return the "from" address
	 */
	public EmailAddress getMailFrom() {
		return mailFrom;
	}

	/**
	 * Sets the "from" address ("MAIL" command).
	 * @param mailFrom the "from" address
	 */
	public void setMailFrom(EmailAddress mailFrom) {
		this.mailFrom = mailFrom;
	}

	public List<EmailAddress> getRecipients() {
		return recipients;
	}

	public void setRecipients(List<EmailAddress> recipients) {
		this.recipients = recipients;
	}

	public void addRecipient(EmailAddress recipient) {
		recipients.add(recipient);
	}

	public EmailData getData() {
		return data;
	}

	public void setData(EmailData data) {
		this.data = data;
	}
}
