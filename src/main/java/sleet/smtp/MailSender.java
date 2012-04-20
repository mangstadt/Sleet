package sleet.smtp;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

import sleet.db.DbDao;
import sleet.db.DirbyMemoryDbDao;
import sleet.db.OutboundEmailGroup;
import sleet.email.Email;
import sleet.email.EmailAddress;
import sleet.email.EmailRaw;

/**
 * Responsible for sending emails and periodically attempting to resend the
 * emails that could not be delivered.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class MailSender {
	private static final Logger logger = Logger.getLogger(MailSender.class.getName());

	/**
	 * The interval in seconds that the database will be polled at to see if
	 * there are any emails that need to be sent or re-tried.
	 */
	private long heartBeat = 10000;

	/**
	 * Emails will be re-tried *more* frequently the first couple times it
	 * attempts to send them.
	 */
	private long transientRetryInterval = 1000 * 60 * 30;

	/**
	 * Emails will be re-tried *less* frequently after the first couple times it
	 * attempts to send them.
	 */
	private long retryInterval = 1000 * 60 * 60;

	/**
	 * If an email couldn't be sent after this amount of time, then give up
	 * trying to send it.
	 */
	private long giveUpInterval = 1000 * 60 * 60 * 24 * 5;

	/**
	 * The address that all error emails will be from.
	 */
	private EmailAddress errorSender;

	/**
	 * The host name of our server.
	 */
	private String hostName;

	/**
	 * Records all client/server communication for logging purposes.
	 */
	private File transactionLogFile;

	/**
	 * The database DAO.
	 */
	private final DbDao dao;

	/**
	 * The emails that are currently being sent.
	 */
	private final Collection<OutboundEmailGroup> beingSentGroups = new LinkedList<OutboundEmailGroup>();

	public static void main(String args[]) throws Exception {
		Email email = new Email();
		email.setFrom(new EmailAddress("test@mangstadt.dyndns.org", "Bob"));
		email.addTo(new EmailAddress("mike.angstadt@gmail.com"));
		//email.addTo(new EmailAddress("foo@mangstadt.dyndns.org"));
		//email.addTo(new EmailAddress("postmaster@mangstadt.dyndns.org"));
		//email.to.add(new EmailAddress("test@mangstadt.dyndns.org"));
		email.setSubject("Important!");
		email.setBody(".My first\n.email message!\r..woo!");

		MailSender mailSender = new MailSender(new DirbyMemoryDbDao());
		mailSender.setHostName("mangstadt.dyndns.org");
		mailSender.setTansientRetryInterval(5000);
		mailSender.setRetryInterval(5000);
		mailSender.setTransactionLogFile(new File("smtp-client-transactions.log"));
		mailSender.sendEmail(email);
		mailSender.start();
	}

	/**
	 * @param dao the database DAO
	 */
	public MailSender(DbDao dao) {
		this.dao = dao;
	}

	public void setTransactionLogFile(File transactionLogFile) {
		this.transactionLogFile = transactionLogFile;
	}

	/**
	 * Sets the host name of our server.
	 * @param hostName the host name of our server
	 */
	public void setHostName(String hostName) {
		if (hostName == null) {
			throw new IllegalArgumentException("Host name cannot be null.");
		}
		this.hostName = hostName;
		if (errorSender == null) {
			setErrorSender(new EmailAddress("postmaster@" + hostName));
		}
	}

	/**
	 * Sets the email that all error emails will be from. Defaults to
	 * "postmaster@HOSTNAME".
	 * @param errorSender the error email sender
	 */
	public void setErrorSender(EmailAddress errorSender) {
		if (errorSender == null) {
			throw new IllegalArgumentException("Error sender cannot be null.");
		}
		this.errorSender = errorSender;
	}

	/**
	 * The frequency at which the database will be polled for emails to send.
	 * @param heartBeat the poll interval in milliseconds
	 */
	public void setHeartBeat(long heartBeat) {
		if (heartBeat < 1) {
			throw new IllegalArgumentException("Heartbeat must be a positive integer.");
		}
		this.heartBeat = heartBeat;
	}

	public void setTansientRetryInterval(long transientRetryInterval) {
		if (transientRetryInterval < 1) {
			throw new IllegalArgumentException("Must be a positive integer.");
		}
		this.transientRetryInterval = transientRetryInterval;
	}

	public void setRetryInterval(long retryInterval) {
		if (retryInterval < 1) {
			throw new IllegalArgumentException("Must be a positive integer.");
		}
		this.retryInterval = retryInterval;
	}

	public void setGiveUpInterval(long giveUpInterval) {
		if (giveUpInterval < 1) {
			throw new IllegalArgumentException("Must be a positive integer.");
		}
		this.giveUpInterval = giveUpInterval;
	}

	/**
	 * Starts the mail sender.
	 * @throws SQLException
	 * @throws IOException
	 */
	public void start() throws SQLException, IOException {
		//make sure all config parameters have been set
		if (hostName == null) {
			throw new IllegalStateException("Host name must be set.");
		}

		while (true) {
			Map<String, List<OutboundEmailGroup>> groupsByHost = dao.selectOutboundEmailGroupsToSend(transientRetryInterval, retryInterval);
			for (Map.Entry<String, List<OutboundEmailGroup>> groupByHost : groupsByHost.entrySet()) {
				String host = groupByHost.getKey();
				List<OutboundEmailGroup> groups = groupByHost.getValue();

				//check to see if any of the emails returned from the database query are already in the process of being sent
				List<OutboundEmailGroup> toSend = new LinkedList<OutboundEmailGroup>();
				synchronized (beingSentGroups) {
					for (OutboundEmailGroup group : groups) {
						boolean found = false;
						for (OutboundEmailGroup beingSentGroup : beingSentGroups) {
							if (group.id == beingSentGroup.id) {
								found = true;
								break;
							}
						}

						if (!found) {
							toSend.add(group);
							beingSentGroups.add(group);
						}
					}
				}

				SenderThread t = new SenderThread(host, toSend);
				t.start();
			}

			try {
				Thread.sleep(heartBeat);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	/**
	 * Queues an email for sending.
	 * @param email the email to send
	 */
	public void sendEmail(Email email) throws SQLException {
		//set the "Date" header if it's not already set
		Date date = email.getHeaders().getDate();
		if (date == null) {
			date = new Date();
			email.getHeaders().setDate(date);
		}

		//"Message-ID" header must be set on all emails
		//give the email an ID if it doesn't have one already
		String id = email.getHeaders().getMessageId();
		if (id == null) {
			id = UUID.randomUUID().toString();
			email.getHeaders().setMessageId(id);
		}

		EmailRaw emailRaw = email.getEmailRaw();

		//create database email object
		sleet.db.Email dbEmail = new sleet.db.Email();
		dbEmail.sender = emailRaw.getMailFrom();
		dbEmail.recipients = emailRaw.getRecipients();
		dbEmail.data = emailRaw.getData();

		//group recipients by host
		Map<String, OutboundEmailGroup> groups = new HashMap<String, OutboundEmailGroup>();
		for (EmailAddress recipient : emailRaw.getRecipients()) {
			String host = recipient.getHost();
			OutboundEmailGroup group = groups.get(host);
			if (group == null) {
				group = new OutboundEmailGroup();
				group.email = dbEmail;
				group.host = host;
				groups.put(host, group);
			}
			group.recipients.add(recipient);
		}

		//insert email into database and add to outbound queue
		synchronized (dao) {
			try {
				dao.insertEmail(dbEmail);
				for (OutboundEmailGroup group : groups.values()) {
					dao.insertOutboundEmailGroup(group);
				}
				dao.commit();
			} catch (SQLException e) {
				dao.rollback();
				throw e;
			}
		}
	}

	/**
	 * Sends an email and then updates the database according to whether it was
	 * successfully sent or not.
	 * @author Mike Angstadt [mike.angstadt@gmail.com]
	 */
	private class SenderThread extends Thread {
		private final String host;
		private final List<OutboundEmailGroup> groups;

		/**
		 * @param host the email host (e.g. "gmail.com")
		 * @param groups the emails that are destined for this host
		 */
		public SenderThread(String host, List<OutboundEmailGroup> groups) {
			this.host = host;
			this.groups = groups;
		}

		@Override
		public void run() {
			//get SMTP server addresses
			List<String> smtpHosts = MxRecordResolver.resolveSmtpServers(host);

			//open SMTP connection
			SMTPOutboundConnection smtpClient = null;
			for (String smtpHost : smtpHosts) {
				try {
					smtpClient = new SMTPOutboundConnection(hostName, smtpHost, 25);
					break;
				} catch (Exception e) {
					//this SMTP address didn't work, try the next one
					logger.log(Level.INFO, "Problem connecting to SMTP server \"" + smtpHost + "\".", e);
				}
			}

			//if none of the SMTP addresses worked, then we can't send the email
			if (smtpClient == null) {
				logger.info("All SMTP servers down for \"" + host + "\".");

				//update each group
				for (OutboundEmailGroup group : groups) {
					if (group.attempts == 0) {
						group.firstAttempt = new Date();
					}
					group.attempts++;
					group.prevAttempt = new Date();
					group.failures.add("Could not connect to any SMTP servers: " + smtpHosts);
				}

				//persist groups to database
				synchronized (dao) {
					try {
						for (OutboundEmailGroup group : groups) {
							//update the outbound email group in the database
							dao.updateOutboundEmailGroup(group);
						}
						dao.commit();
					} catch (SQLException e) {
						logger.log(Level.SEVERE, "Problem updating OutboundEmailGroups in database.", e);
						dao.rollback();
					}
				}

				synchronized (beingSentGroups) {
					beingSentGroups.removeAll(groups);
				}

				//terminate the thread
				return;
			}

			//SMTP connection successfully established
			//so send the email(s)
			try {
				for (OutboundEmailGroup group : groups) {
					boolean lastAttempt = false;
					if (group.attempts == 0) {
						group.firstAttempt = new Date();
					} else {
						//how long ago the first attempt was
						long diffFirstAttempt = System.currentTimeMillis() - group.firstAttempt.getTime();

						//is this the last attempt for this email?
						if (diffFirstAttempt > giveUpInterval) {
							lastAttempt = true;
						}
					}
					group.attempts++;
					group.prevAttempt = new Date();

					boolean error = false;
					try {
						//send the email
						SendResult sendResult = smtpClient.sendEmail(group.recipients, group.email);

						if (sendResult.failedAddresses.isEmpty()) {
							logger.info("Email successfully sent to " + group.recipients);
						} else {
							//the server rejected one or more recipient addresses, so send an error email to the original sender
							//this is still considered a successfully sent email though, in that it will be removed from the outbound emails list

							logger.info("Server rejected these email addresses: " + sendResult.failedAddresses + "\nbut the email was successfully delivered to: " + sendResult.successfulAddresses);

							Email errorEmail = new Email();
							errorEmail.setFrom(errorSender);
							errorEmail.addTo(group.email.sender);
							errorEmail.setSubject("Postmaster Notification: Email could not be delivered");

							StringBuilder body = new StringBuilder();
							body.append("Delivery failed for the following recipient(s): ");
							for (int i = 0; i < sendResult.failedAddresses.size(); i++) {
								EmailAddress recipient = sendResult.failedAddresses.get(i);
								String msg = sendResult.failedAddressesMessages.get(i);
								body.append(recipient.getAddress() + " - " + msg);
								body.append("\n");
							}

							if (!sendResult.successfulAddresses.isEmpty()) {
								body.append("\nHowever, delivery SUCCEEDED for these recipient(s): ");
								for (EmailAddress recipient : sendResult.successfulAddresses) {
									body.append(recipient.getAddress());
									body.append("\n");
								}
							}

							body.append("\nOriginal message is as follows ===============\n\n");
							body.append(group.email.data.toData()); //TODO should use raw data, not over-the-wire data
							errorEmail.setBody(body.toString());

							sendEmail(errorEmail);
						}
					} catch (Exception e) {
						group.failures.add("Problem sending email.\n" + ExceptionUtils.getStackTrace(e));
						logger.log(Level.INFO, "Cannot send email (attempt # " + group.attempts + ").", e);
						error = true;
					}

					if (error) {
						//email couldn't be sent

						if (lastAttempt) {
							//give up trying to send the mail
							//send an error email to the original sender

							Email errorEmail = new Email();
							errorEmail.setFrom(errorSender);
							errorEmail.addTo(group.email.sender);
							errorEmail.setSubject("Postmaster Notification: Email could not be delivered");

							StringBuilder body = new StringBuilder();
							body.append("Delivery failed for the following recipient(s): ");
							for (int i = 0; i < group.recipients.size(); i++) {
								EmailAddress recipient = group.recipients.get(i);
								body.append(recipient.getAddress());
								body.append("\n");
							}
							body.append("\nOriginal message is as follows ===============\n\n");
							body.append(group.email.data.toData()); //TODO should use raw data, not over-the-wire data
							errorEmail.setBody(body.toString());

							sendEmail(errorEmail);

							synchronized (dao) {
								try {
									dao.deleteEmail(group.email);
									dao.commit();
								} catch (SQLException e) {
									dao.rollback();
									throw e;
								}
							}
						} else {
							//update the outbound email group in the database
							synchronized (dao) {
								try {
									dao.updateOutboundEmailGroup(group);
									dao.commit();
								} catch (SQLException e) {
									dao.rollback();
									throw e;
								}
							}
						}
					} else {
						//there were no errors sending the email

						synchronized (dao) {
							try {
								//mark the email as having been successfully sent
								dao.insertOutboxEmail(group.email);

								//delete it from the outbound queue
								dao.deleteOutboundEmailGroup(group);

								dao.commit();
							} catch (SQLException e) {
								dao.rollback();
								throw e;
							}
						}
					}

					synchronized (beingSentGroups) {
						beingSentGroups.remove(group);
					}
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error in SenderThread.", e);
			} finally {
				//close connection to SMTP server
				try {
					smtpClient.close();
				} catch (Exception e) {
					logger.log(Level.WARNING, "Problem closing SMTP connection.", e);
				}

				//log the SMTP communication
				if (transactionLogFile != null) {
					synchronized (transactionLogFile) {
						try {
							smtpClient.getTransactionLog().writeToFile(transactionLogFile);
						} catch (IOException e) {
							logger.log(Level.WARNING, "Problem writing to transaction log.", e);
						}
					}
				}

				synchronized (beingSentGroups) {
					beingSentGroups.removeAll(groups);
				}
			}
		}
	}
}
