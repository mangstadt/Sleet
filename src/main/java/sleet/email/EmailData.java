package sleet.email;

import java.util.Scanner;
import java.util.logging.Logger;

import static sleet.email.EmailRaw.CRLF;

/**
 * Represents the DATA portion of an email.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class EmailData {
	private static final Logger logger = Logger.getLogger(EmailData.class.getName());

	private EmailHeaders headers;

	//TODO MIME parts
	private String body;

	public EmailData() {
		headers = new EmailHeaders();
	}

	/**
	 * The raw data from the DATA command of the SMTP message.
	 * @param data the data. It is assumed that:
	 * <ul>
	 * <li>dot-stuffing is ENABLED</li>
	 * <li>there is NO "&lt;CRLF&gt;.&lt;CRLF&gt;" terminator on the end</li>
	 * </ul>
	 */
	public EmailData(String data) {
		headers = parseHeaders(data);
		body = parseBody(data);
	}

	/**
	 * Parses the email headers into a friendly object.
	 * @param data the email DATA
	 * @return the headers
	 */
	private EmailHeaders parseHeaders(String data) {
		EmailHeaders headers = new EmailHeaders();
		Scanner scanner = new Scanner(data);
		String curName = null;
		String curValue = null;
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();

			if (line.isEmpty()) {
				//end of header section
				break;
			} else if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
				//header values can span multiple lines
				//we are "unfolding" the header value
				//RFC 5322, p.8

				//the folded header value can start with multiple whitespace characters
				//ignore all but the first I guess
				//RFC 5322, p.12
				int i = 1;
				while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
					i++;
				}
				if (i < line.length()) {
					//folded header lines can consist entirely of whitespace
					//if so, then ignore the line
					//see RFC-5322, p.51
					curValue += " " + line.substring(i).trim();
				}
			} else {
				if (curName != null) {
					headers.addHeader(curName, curValue);
				}

				int colon = line.indexOf(':');
				if (colon == -1) {
					//this should never happen
					logger.warning("Skipping malformed header line: " + line);
					continue;
				}

				curName = line.substring(0, colon);

				//header names can have whitespace in them
				//see RFC-5322, p.51
				curName = curName.trim();

				if (colon < line.length() - 1) {
					curValue = line.substring(colon + 1).trim();
				} else {
					curValue = "";
				}
			}
		}

		if (curName != null) {
			headers.addHeader(curName, curValue);
		}

		return headers;
	}

	/**
	 * Parses the body of an email out of an email DATA string.
	 * @return the body of the email with:
	 * <ul>
	 * <li>dot-stuffing REMOVED</li>
	 * <li>"\r\n" newlines</li>
	 * <li>does NOT include the "&lt;CRLF&gt;.&lt;CRLF&gt;" terminator on the
	 * end</li>
	 * </ul>
	 */
	private String parseBody(String data) {
		String emptyLine = CRLF + CRLF;

		//the first empty line separates the headers from the body
		int emptyLinePos = data.indexOf(emptyLine);

		if (emptyLinePos == -1) {
			//there is no body
			return null;
		} else {
			int bodyPos = emptyLinePos + emptyLine.length();
			if (bodyPos < data.length() - 1) {
				String body = data.substring(bodyPos);
				return removeDotStuffing(body);
			} else {
				return "";
			}
		}
	}

	public EmailHeaders getHeaders() {
		return headers;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	/**
	 * Converts this object into a string for sending over the wire with the
	 * SMTP "DATA" command. Does not include the terminating ".".
	 * @return the string for sending over the wire
	 */
	public String toData() {
		StringBuilder data = new StringBuilder();

		//headers
		for (EmailHeader header : headers) {
			String name = header.getName();
			String value = header.getValue();
			String line = name + ": " + value;
			data.append(foldHeader(line, 78));
			data.append(CRLF);
		}

		if (body != null) {
			//append the email body
			Scanner scanner = new Scanner(body);
			while (scanner.hasNextLine()) {
				data.append(CRLF);

				String line = scanner.nextLine();

				//"dot-stuff" the line
				//(adds an extra "." to the beginning of all lines that start with "."
				if (line.startsWith(".")) {
					line = "." + line;
				}

				//lines should not be longer than 78 chars
				//RFC 5322, p.7
				line = wrapBody(line, 78);

				data.append(line);
			}
		}

		return data.toString();
	}

	/**
	 * Insures that each header line does not exceed a certain length (called
	 * "folding" the header). This method has "protected" access so it can be
	 * unit tested.
	 * @param line the full header line in the format of "NAME: VALUE".
	 * @param maxLength the max length each line can be
	 * @return the folded header lines
	 * @see RFC 5322, p.8
	 */
	protected static String foldHeader(String line, int maxLength) {
		if (line.length() <= maxLength) {
			return line;
		}

		StringBuilder sb = new StringBuilder();
		int cur = 0;
		boolean firstLine = true;
		while (cur < line.length()) {
			if (!firstLine) {
				sb.append(CRLF).append(" ");
			}

			int end = cur + maxLength;
			if (end > line.length()) {
				end = line.length();
			}
			String sub = line.substring(cur, end);

			int space = sub.lastIndexOf(" ");
			if (space == -1) {
				sb.append(sub);
				cur += maxLength;
			} else {
				sb.append(sub.substring(0, space));
				cur += space + 1;
			}

			if (firstLine) {
				//after the first line, the max length is decremented because all subsequent lines must begin with a space
				maxLength--;
				firstLine = false;
			}
		}

		return sb.toString();
	}

	/**
	 * Ensures that each line in the email body does not exceed a certain
	 * length. This method has "protected" access so it can be unit tested.
	 * @param line the line
	 * @param maxLength the max length the line can be
	 * @return the line with CRLF chars inserted where need be
	 */
	protected static String wrapBody(String line, int maxLength) {
		if (line.length() <= maxLength) {
			return line;
		}

		StringBuilder sb = new StringBuilder();
		int cur = 0;
		while (cur < line.length()) {
			if (cur > 0) {
				sb.append(CRLF);
			}

			int end = cur + maxLength;
			if (end > line.length()) {
				end = line.length();
			}
			String sub = line.substring(cur, end);
			int space = sub.lastIndexOf(" ");
			if (space == -1) {
				sb.append(sub);
				cur += maxLength;
			} else {
				sb.append(sub.substring(0, space));
				cur += space + 1;
			}
		}

		return sb.toString();
	}

	/**
	 * Removes dot-stuffing from a string. This method has "protected" access so
	 * it can be unit tested.
	 * @param str the string
	 * @return the string with dot-stuffing removed
	 */
	protected static String removeDotStuffing(String str) {
		return str.replaceAll("(^|\\n)\\.\\.", "$1.");
	}
}
