package sleet.email;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class EmailHeadersTest {
	@Test
	public void getAddressHeader() {
		String toHeader = "";

		//special chars in a quoted string
		toHeader += "\"George, \\\"A. <Washington>\"   <gwashington@virginia.gov>, ";

		//a group
		toHeader += "Vice Presidents: jadams@massachusetts.gov ; ";

		//another group
		toHeader += "More Vice Presidents: \"Thomas Jefferson\" <tjefferson@virginia.gov>, Aaron Burr <aburr@newyork.gov> ;";

		//comments
		toHeader += "James (4th President of the US) Madison <jmadison(his account)@virginia.gov(his host)> ,";

		//an address whose name is not surrounded with quotes
		toHeader += "James Monroe <jmonroe@virginia.gov>,";

		//an extra comma
		toHeader += " , ";

		//an email address with spaces throughout it
		toHeader += "j q adams@massachuse tts . gov";

		EmailHeaders headers = new EmailHeaders();
		headers.setHeader("To", toHeader);

		AddressHeader header = headers.getAddressHeader("To");
		List<EmailAddress> emails = header.getAddresses();
		assertEquals(4, emails.size());
		EmailAddress email = emails.get(0);
		assertEquals("George, \"A. <Washington>", email.getName());
		assertEquals("gwashington@virginia.gov", email.getAddress());
		email = emails.get(1);
		assertEquals("James  Madison", email.getName());
		assertEquals("jmadison@virginia.gov", email.getAddress());
		email = emails.get(2);
		assertEquals("James Monroe", email.getName());
		assertEquals("jmonroe@virginia.gov", email.getAddress());
		email = emails.get(3);
		assertEquals(null, email.getName());
		assertEquals("jqadams@massachusetts.gov", email.getAddress());

		List<EmailGroup> groups = header.getGroups();
		assertEquals(2, groups.size());

		EmailGroup group = groups.get(0);
		assertEquals(1, group.getAddresses().size());
		assertEquals("Vice Presidents", group.getName());
		assertEquals(null, group.getAddresses().get(0).getName());
		assertEquals("jadams@massachusetts.gov", group.getAddresses().get(0).getAddress());

		group = groups.get(1);
		assertEquals(2, group.getAddresses().size());
		assertEquals("More Vice Presidents", group.getName());
		assertEquals("Thomas Jefferson", group.getAddresses().get(0).getName());
		assertEquals("tjefferson@virginia.gov", group.getAddresses().get(0).getAddress());
		assertEquals("Aaron Burr", group.getAddresses().get(1).getName());
		assertEquals("aburr@newyork.gov", group.getAddresses().get(1).getAddress());
	}

	@Test
	public void getIdHeader() {
		EmailHeaders headers = new EmailHeaders();
		headers.setHeader("References", "<123@host.  com>  ignore this   <456 @host.com>");

		List<String> ids = headers.getIdHeader("References");
		assertEquals(2, ids.size());
		assertEquals("123@host.com", ids.get(0));
		assertEquals("456@host.com", ids.get(1));
	}
}
