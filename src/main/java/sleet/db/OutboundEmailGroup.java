package sleet.db;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import sleet.email.EmailAddress;

public class OutboundEmailGroup {
	public Integer id;
	public String host;
	public Email email;
	public List<EmailAddress> recipients = new LinkedList<EmailAddress>();
	public int attempts = 0;
	public Date firstAttempt = null;
	public Date prevAttempt = null;
	public List<String> failures = new ArrayList<String>();
}
