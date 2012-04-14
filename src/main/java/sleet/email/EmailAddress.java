package sleet.email;

/**
 * Represents an email address.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class EmailAddress {
	/**
	 * The full email address.
	 */
	private final String address;

	/**
	 * The mailbox part of the email address (the text before the "@").
	 */
	private final String mailbox;

	/**
	 * The host part of the email address (the text after the "@").
	 */
	private final String host;

	/**
	 * The real-life person name that's associated with the email address or
	 * null for no name.
	 */
	private final String name;

	/**
	 * @param address the full email address
	 */
	public EmailAddress(String address) {
		this(address, null);
	}

	/**
	 * @param address the full email address
	 * @param name the name associated with the address
	 */
	public EmailAddress(String address, String name) {
		this.address = address;
		this.name = name;

		int at = address.indexOf('@');
		if (at == -1) {
			mailbox = address;
			host = null;
		} else {
			mailbox = address.substring(0, at);
			if (at < address.length() - 1) {
				host = address.substring(at + 1);
			} else {
				host = "";
			}
		}
	}

	/**
	 * Determines if the email address syntax is valid.
	 * @return true if the syntax is value, false if not
	 */
	public boolean isValid() {
		//TODO create a better check
		return address.contains("@");
	}

	@Override
	public String toString() {
		return address;
	}

	public String getAddress() {
		return address;
	}

	public String getHost() {
		return host;
	}

	public String getMailbox() {
		return mailbox;
	}

	public String getName() {
		return name;
	}
}