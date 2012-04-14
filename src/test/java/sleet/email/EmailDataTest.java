package sleet.email;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EmailDataTest {
	@Test
	public void foldHeader() {
		String input, expected, actual;

		input = "From: <foo@bar.com>";
		expected = input;
		actual = EmailData.foldHeader(input, 78);
		assertEquals(expected, actual);

		input = "From: <foo@bar.com>, <bar@foo.com>";
		expected = "From: <foo@bar.com>,\r\n <bar@foo.com>";
		actual = EmailData.foldHeader(input, 25);
		assertEquals(expected, actual);

		input = "From: <foo@bar.com>, <bar@foo.com>";
		expected = "From:\r\n <foo@bar.com>,\r\n <bar@foo.com>";
		actual = EmailData.foldHeader(input, 18);
		assertEquals(expected, actual);

		input = "From: <foo@bar.com>";
		expected = "From:\r\n <foo@bar.\r\n com>";
		actual = EmailData.foldHeader(input, 10);
		assertEquals(expected, actual);
	}

	@Test
	public void wrapBody() {
		String input, expected, actual;

		input = "This is an email message addressed to you. Internationalization.";
		expected = "This is an\r\nemail message\r\naddressed to\r\nyou.\r\nInternationaliz\r\nation.";
		actual = EmailData.wrapBody(input, 15);
		assertEquals(expected, actual);
	}
}
