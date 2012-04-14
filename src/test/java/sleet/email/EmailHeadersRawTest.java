package sleet.email;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.Test;

public class EmailHeadersRawTest {
	@Test
	public void addGetHeader() {
		EmailHeadersRaw headers = new EmailHeadersRaw();

		headers.addHeader("One", "1");
		headers.addHeader("One", "2");
		headers.addHeader("Two", "2");

		List<String> expected = Arrays.asList(new String[] { "1", "2" });
		List<String> actual = headers.getHeaders("One");
		assertEquals(expected, actual);

		String expectedStr = "1";
		String actualStr = headers.getHeader("One");
		assertEquals(expectedStr, actualStr);

		expected = Arrays.asList(new String[] { "2" });
		actual = headers.getHeaders("Two");
		assertEquals(expected, actual);

		expectedStr = "2";
		actualStr = headers.getHeader("Two");
		assertEquals(expectedStr, actualStr);

		expected = Arrays.asList(new String[] {});
		actual = headers.getHeaders("Three");
		assertEquals(expected, actual);

		expectedStr = null;
		actualStr = headers.getHeader("Three");
		assertEquals(expectedStr, actualStr);

		//headers should be sorted in the order that they were added
		Iterator<EmailHeader> it = headers.iterator();
		EmailHeader header = it.next();
		assertEquals("One", header.getName());
		assertEquals("1", header.getValue());
		header = it.next();
		assertEquals("One", header.getName());
		assertEquals("2", header.getValue());
		header = it.next();
		assertEquals("Two", header.getName());
		assertEquals("2", header.getValue());
		try {
			it.next();
			fail();
		} catch (NoSuchElementException e) {
			//should be thrown
		}
	}

	@Test
	public void prependHeader() {
		EmailHeadersRaw headers = new EmailHeadersRaw();
		headers.addHeader("One", "1");
		headers.addHeader("Two", "2");
		headers.prependHeader("Three", "3");

		Iterator<EmailHeader> it = headers.iterator();
		EmailHeader header = it.next();
		assertEquals("Three", header.getName());
		assertEquals("3", header.getValue());
		header = it.next();
		assertEquals("One", header.getName());
		assertEquals("1", header.getValue());
		header = it.next();
		assertEquals("Two", header.getName());
		assertEquals("2", header.getValue());
		try {
			it.next();
			fail();
		} catch (NoSuchElementException e) {
			//should be thrown
		}
	}

	@Test
	public void setHeader() {
		EmailHeadersRaw headers = new EmailHeadersRaw();
		headers.addHeader("One", "1");
		headers.addHeader("One", "2");
		headers.setHeader("One", "3");

		List<String> expected = Arrays.asList(new String[] { "3" });
		List<String> actual = headers.getHeaders("One");
		assertEquals(expected, actual);
	}

	@Test
	public void removeComments() {
		String input, expected, actual;

		//the same string should be returned if there are no comments
		input = "The value";
		expected = "The value";
		actual = EmailHeadersRaw.removeComments(input);
		assertEquals(expected, actual);

		//comments should be removed
		input = "The value (a comment) value.";
		expected = "The value  value.";
		actual = EmailHeadersRaw.removeComments(input);
		assertEquals(expected, actual);

		//comments can be nested within comments
		input = "The value (a (comment within a comment) comment ) value.";
		expected = "The value  value.";
		actual = EmailHeadersRaw.removeComments(input);
		assertEquals(expected, actual);

		//if there's no closing paren, then delete everything after the opening paren 
		input = "The value (comment";
		expected = "The value ";
		actual = EmailHeadersRaw.removeComments(input);
		assertEquals(expected, actual);

		//characters, including parens, can be escaped
		input = "The \\\\ \\a value \\( escaped \\)";
		expected = "The \\\\ \\a value \\( escaped \\)";
		actual = EmailHeadersRaw.removeComments(input);
		assertEquals(expected, actual);
	}

	@Test
	public void escape() {
		String input, expected, actual;

		input = "Text \\ (parens)";
		expected = "Text \\\\ \\(parens\\)";
		actual = EmailHeadersRaw.escape(input);
		assertEquals(expected, actual);
	}

	@Test
	public void unescape() {
		String input, expected, actual;

		input = "Text \\\\ \\(parens\\)";
		expected = "Text \\ (parens)";
		actual = EmailHeadersRaw.unescape(input);
		assertEquals(expected, actual);
	}
}
