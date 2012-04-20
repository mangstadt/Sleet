package sleet.smtp;

import java.util.ArrayList;
import java.util.List;

import sleet.email.EmailAddress;

/**
 * Contains the results of sending an email.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SendResult {
	/**
	 * The addresses that could not be delivered to.
	 */
	public final List<EmailAddress> failedAddresses = new ArrayList<EmailAddress>();

	/**
	 * The addresses that successfully received the email.
	 */
	public final List<EmailAddress> successfulAddresses = new ArrayList<EmailAddress>();

	/**
	 * The specific error messages that the server returned for the failed
	 * addresses.
	 */
	public final List<String> failedAddressesMessages = new ArrayList<String>();
}
