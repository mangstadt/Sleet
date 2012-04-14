package sleet.email;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group that appears in an email address header.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class EmailGroup {
	private String name;

	private List<EmailAddress> addresses = new ArrayList<EmailAddress>();

	public EmailGroup(String name) {
		setName(name);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<EmailAddress> getAddresses() {
		return addresses;
	}

	public void addAddress(EmailAddress address) {
		addresses.add(address);
	}

	public void setAddresses(List<EmailAddress> addresses) {
		this.addresses = addresses;
	}
}
