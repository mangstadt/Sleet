package sleet.smtp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Logger;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

/**
 * Gets the SMTP servers of an email address (the host part of an email address
 * is not always the address of the actual SMTP server).
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class MxRecordResolver {
	private static final Logger logger = Logger.getLogger(MxRecordResolver.class.getName());

	private static final Hashtable<String, String> dirContextEnv;
	static {
		dirContextEnv = new Hashtable<String, String>();
		dirContextEnv.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
	}

	/**
	 * Gets the address to the SMTP server of the given email host.
	 * @param host the email host (e.g. "gmail.com")
	 * @return the SMTP servers of the email host, ordered by preference
	 * indicator (the servers at the beginning of the list should be tried
	 * first)
	 * @see http://www.rgagnon.com/javadetails/java-0452.html
	 * @see http://stackoverflow.com/questions/9475073/java-sockets-over-the-internet-connectexception-operation-timed-out
	 */
	public static List<String> resolveSmtpServers(String host) {
		List<String> smtpHosts = new ArrayList<String>();

		//for testing purposes
//		if ("mangstadt.dyndns.org".equals(host)) {
//			host = "localhost";
//		}

		try {
			DirContext ictx = new InitialDirContext(dirContextEnv);
			Attributes attrs = ictx.getAttributes(host, new String[] { "MX" });
			Attribute mxAttr = attrs.get("MX");
			if (mxAttr == null || mxAttr.size() == 0) {
				//there are no MX records, so just use the host name
				logger.info("No MX records found for host " + host);
				smtpHosts.add(host);
			} else {
				//get the list of SMTP servers, along with their preference indicators
				List<MxRecord> mxRecords = new ArrayList<MxRecord>(mxAttr.size());
				for (int i = 0; i < mxAttr.size(); i++) {
					String mx = (String) mxAttr.get(i);
					int space = mx.indexOf(' ');
					int preference = Integer.parseInt(mx.substring(0, space));
					String server = mx.substring(space + 1);
					mxRecords.add(new MxRecord(preference, server));
				}

				//RPC 5321 p.70 - if there are multiple servers with identical preference indicators, then spread the load evenly amongst all of them by randomizing which one is chosen
				//shuffling the list before sorting should accomplish this
				Collections.shuffle(mxRecords);

				//sort the SMTP servers by preference indicator ascending
				//RPC 5321 p.70 - the server with the lowest preference indicator should be tried first
				Collections.sort(mxRecords);

				for (MxRecord mxRecord : mxRecords) {
					smtpHosts.add(mxRecord.smtpHost);
				}

				logger.info("SMTP server(s) for host " + host + " are: " + smtpHosts);
			}
		} catch (NamingException e) {
			smtpHosts.add(host);
		}

		return smtpHosts;
	}

	/**
	 * Represents an MX record.
	 * @author Mike Angstadt [mike.angstadt@gmail.com]
	 */
	private static class MxRecord implements Comparable<MxRecord> {
		/**
		 * The preference indicator.
		 */
		public final int preference;

		/**
		 * The address of the SMTP host.
		 */
		public final String smtpHost;

		/**
		 * @param preference the preference indicator
		 * @param smtpHost the address of the SMTP host
		 */
		public MxRecord(int preference, String smtpHost) {
			this.preference = preference;
			this.smtpHost = smtpHost;
		}

		@Override
		public int compareTo(MxRecord that) {
			return this.preference - that.preference;
		}
	}
}
