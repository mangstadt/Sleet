package sleet.db;

import java.util.ArrayList;
import java.util.List;

import sleet.email.EmailAddress;
import sleet.email.EmailData;

/**
 * Database DTO for "emails" table.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class Email {
	/**
	 * The row primary key.
	 */
	public Integer id;
	
	/**
	 * The "from" address.
	 */
	public EmailAddress sender;
	
	/**
	 * The "to" addresses.
	 */
	public List<EmailAddress> recipients = new ArrayList<EmailAddress>();
	
	/**
	 * The data portion of the email.
	 */
	public EmailData data;
}
