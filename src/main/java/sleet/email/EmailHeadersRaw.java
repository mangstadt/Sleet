package sleet.email;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Contains the headers that are part of the DATA portion of an email.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class EmailHeadersRaw implements Iterable<EmailHeader> {
	private final List<EmailHeader> headerList = new LinkedList<EmailHeader>();

	/**
	 * Adds a header to the beginning of the header list.
	 * @param name the header name
	 * @param value the header value
	 */
	public void prependHeader(String name, String value) {
		EmailHeader header = new EmailHeader(name, value);
		headerList.add(0, header);
	}

	/**
	 * Adds a header to the end of the header list.
	 * @param name the header name
	 * @param value the header value
	 */
	public void addHeader(String name, String value) {
		EmailHeader header = new EmailHeader(name, value);
		headerList.add(header);
	}

	/**
	 * Replaces any existing headers with the same name or creates a new header
	 * if no headers with the given name exist.
	 * @param name the header name
	 * @param value the new header value
	 */
	public void setHeader(String name, String value) {
		//remove existing headers with the same name
		List<EmailHeader> headers = getHeaderObjs(name);
		for (EmailHeader header : headers) {
			headerList.remove(header);
		}

		addHeader(name, value);
	}

	/**
	 * Gets all headers with the given name.
	 * @param name the header name
	 * @return the headers with the given name or empty list if none were found
	 */
	private List<EmailHeader> getHeaderObjs(String name) {
		List<EmailHeader> headers = new ArrayList<EmailHeader>();
		for (EmailHeader header : headerList) {
			if (header.getName().equalsIgnoreCase(name)) {
				headers.add(header);
			}
		}
		return headers;
	}

	/**
	 * Gets the value of the first header found with this name.
	 * @param name the header name
	 * @return the header value or null if not found
	 */
	public String getHeader(String name) {
		List<EmailHeader> headers = getHeaderObjs(name);
		return headers.isEmpty() ? null : headers.get(0).getValue();
	}

	/**
	 * Gets all header values that match the given name.
	 * @param name the header name
	 * @return the header values that match this name or empty list if none were
	 * found
	 */
	public List<String> getHeaders(String name) {
		List<String> values = new ArrayList<String>();
		List<EmailHeader> headers = getHeaderObjs(name);
		for (EmailHeader header : headers) {
			values.add(header.getValue());
		}
		return values;
	}

	/**
	 * Iterates over all headers.
	 */
	@Override
	public Iterator<EmailHeader> iterator() {
		return headerList.iterator();
	}

	/**
	 * Removes the comments from a header value (comments are inclosed in
	 * parenthesis). Method has "protected" access for unit testing.
	 * @param value the header value
	 * @return the header value with comments removed
	 */
	protected static String removeComments(String value) {
		int start = -1;
		int deep = 0; //comments can be nested
		char prev = 0;
		int deleteLength = 0;
		StringBuilder sb = new StringBuilder(value);

		for (int i = 0; i < value.length(); i++) {
			char cur = value.charAt(i);

			//ignore the current character if it is escaped
			if (prev == '\\' && (i < 2 || value.charAt(i - 2) != '\\')) {
				continue;
			}

			if (cur == '(') {
				if (deep == 0) {
					start = i;
				}
				deep++;
			} else if (cur == ')') {
				deep--;
				if (deep == 0) {
					sb.delete(start - deleteLength, i + 1 - deleteLength);
					deleteLength += i - start + 1;
				}
			}

			prev = cur;
		}

		if (deep > 0) {
			//if there's an open paran, but no closing paran
			sb.delete(start - deleteLength, sb.length());
		}

		return sb.toString();
	}

	/**
	 * Unescapes any escaped characters in a header value. Method has
	 * "protected" access for unit testing.
	 * @param value the header value
	 * @return the header value with escaped characters unescaped
	 */
	protected static String unescape(String value) {
		return value.replaceAll("\\\\(.)", "$1");
	}

	/**
	 * Escapes any characters that have a special meaning in a header value.
	 * Method has "protected" access for unit testing.
	 * @param value the header value
	 * @return the header value with special characters escpaed
	 */
	protected static String escape(String value) {
		//inserts a backslash before all backslash and parenthesis characters
		return value.replaceAll("[\\\\\\(\\)]", "\\\\$0");
	}
}
