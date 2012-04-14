package sleet.smtp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * Tests the {@link SMTPClientSocket} class.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class SMTPClientSocketTest {
	/**
	 * Tests to make sure it can parse a single-lined response from the server.
	 */
	@Test
	public void singleLinedResponse() throws Exception {
		String input = "220 mx.google.com ESMTP c2si3492693qcd.78\r\n";
		InputStream in = IOUtils.toInputStream(input);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		SMTPClientSocket client = new SMTPClientSocket(in, out);

		SMTPResponse response;

		response = client.connect();
		assertEquals(220, response.getStatusCode());
		assertEquals("mx.google.com ESMTP c2si3492693qcd.78", response.getMessage());
		assertEquals(1, response.getMessages().size());
		assertEquals("mx.google.com ESMTP c2si3492693qcd.78", response.getMessages().get(0));
	}

	/**
	 * Tests to make sure it can parse a multi-lined response from the server.
	 */
	@Test
	public void multiLinedResponse() throws Exception {
		String input = "250-mx.google.com at your service, [68.80.246.118]\r\n250-SIZE 35882577\r\n250-8BITMIME\r\n250-STARTTLS\r\n250 ENHANCEDSTATUSCODES\r\n";
		InputStream in = IOUtils.toInputStream(input);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		SMTPClientSocket client = new SMTPClientSocket(in, out);

		SMTPResponse response;

		response = client.connect();
		assertEquals(250, response.getStatusCode());
		assertEquals("mx.google.com at your service, [68.80.246.118]", response.getMessage());
		assertEquals(5, response.getMessages().size());
		assertEquals("mx.google.com at your service, [68.80.246.118]", response.getMessages().get(0));
		assertEquals("SIZE 35882577", response.getMessages().get(1));
		assertEquals("8BITMIME", response.getMessages().get(2));
		assertEquals("STARTTLS", response.getMessages().get(3));
		assertEquals("ENHANCEDSTATUSCODES", response.getMessages().get(4));
	}

	@Test
	public void connect() throws Exception {
		String input = "220 mx.google.com ESMTP c2si3492693qcd.78\r\n";
		InputStream in = IOUtils.toInputStream(input);

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		SMTPClientSocket client = new SMTPClientSocket(in, out);

		//connect() must be called first
		try {
			client.ehlo("sleet.com");
			fail();
		} catch (IllegalStateException e) {

		}

		SMTPResponse response = client.connect();

		//connect() cannot be called more than once
		try {
			client.connect();
			fail();
		} catch (IllegalStateException e) {

		}

		assertEquals(220, response.getStatusCode());
		assertEquals("mx.google.com ESMTP c2si3492693qcd.78", response.getMessage());
	}

	@Test
	public void ehlo() throws Exception {
		InputStream in = new MockServerInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SMTPClientSocket client = new SMTPClientSocket(in, out);
		client.connect();

		client.ehlo("sleet.com");
		String request = new String(out.toByteArray());
		assertEquals("EHLO sleet.com\r\n", request);
	}

	@Test
	public void helo() throws Exception {
		InputStream in = new MockServerInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SMTPClientSocket client = new SMTPClientSocket(in, out);
		client.connect();

		client.helo("sleet.com");
		String request = new String(out.toByteArray());
		assertEquals("HELO sleet.com\r\n", request);
	}

	@Test
	public void mail() throws Exception {
		InputStream in = new MockServerInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SMTPClientSocket client = new SMTPClientSocket(in, out);
		client.connect();

		client.mail("george.washington@whitehouse.gov");
		String request = new String(out.toByteArray());
		assertEquals("MAIL FROM:<george.washington@whitehouse.gov>\r\n", request);
	}

	@Test
	public void rcpt() throws Exception {
		InputStream in = new MockServerInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SMTPClientSocket client = new SMTPClientSocket(in, out);
		client.connect();

		client.rcpt("george.washington@whitehouse.gov");
		String request = new String(out.toByteArray());
		assertEquals("RCPT TO:<george.washington@whitehouse.gov>\r\n", request);
	}

	@Test
	public void data() throws Exception {
		InputStream in = new MockServerInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		SMTPClientSocket client = new SMTPClientSocket(in, out);
		client.connect();

		client.data();
		String request = new String(out.toByteArray());
		assertEquals("DATA\r\n", request);
		out.reset();

		client.data("Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor");
		request = new String(out.toByteArray());
		assertEquals("Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor\r\n.\r\n", request);
		out.reset();
	}

	/**
	 * Returns the same server message every time.
	 * @author Mike Angstadt [mike.angstadt@gmail.com]
	 */
	private static class MockServerInputStream extends InputStream {
		private InputStream in = IOUtils.toInputStream("250 dummy\r\n");

		@Override
		public int read() throws IOException {
			int read = in.read();
			if (read == -1) {
				in.reset();
			}
			return read;
		}
	}
}
