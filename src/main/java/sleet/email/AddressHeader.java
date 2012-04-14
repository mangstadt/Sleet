package sleet.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an email header that contains email addresses and groups, such as
 * "To" and "Cc".
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class AddressHeader {
	private final List<EmailAddress> addresses = new ArrayList<EmailAddress>();
	private final List<EmailGroup> groups = new ArrayList<EmailGroup>();

	public List<EmailAddress> getAddresses() {
		return addresses;
	}

	public List<EmailGroup> getGroups() {
		return groups;
	}

	public List<EmailAddress> getAllAddresses() {
		List<EmailAddress> emails = new ArrayList<EmailAddress>(addresses);
		for (EmailGroup group : groups) {
			emails.addAll(group.getAddresses());
		}
		return emails;
	}
}
