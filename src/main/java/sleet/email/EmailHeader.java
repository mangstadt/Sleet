package sleet.email;

/**
 * Represents a header in an email message.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class EmailHeader {
	/**
	 * The header name.
	 */
	private String name;

	/**
	 * The header value;
	 */
	private String value;

	/**
	 * @param name the header name
	 * @param value the header value
	 */
	public EmailHeader(String name, String value) {
		this.name = name;
		this.value = value;
	}

	/**
	 * Gets the header name.
	 * @return the header name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the header name.
	 * @param name the header name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Gets the header value.
	 * @return the header value
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Sets the header value.
	 * @param value the header value
	 */
	public void setValue(String value) {
		this.value = value;
	}
}
