package sleet.db;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

import sleet.ClasspathUtils;
import sleet.email.EmailAddress;
import sleet.email.EmailData;

/**
 * Data access object implementation for embedded Derby database.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public abstract class DirbyDbDao implements DbDao {
	private static final Logger logger = Logger.getLogger(DirbyDbDao.class.getName());

	/**
	 * The current version of the database schema.
	 */
	private static final int schemaVersion = 1;

	/**
	 * The database connection.
	 */
	private Connection db;

	/**
	 * Connects to the database and creates the database from scratch if it
	 * doesn't exist.
	 * @param jdbcUrl the JDBC URL
	 * @param create true to create the database schema, false not to
	 * @throws SQLException
	 */
	protected void init(String jdbcUrl, boolean create) throws SQLException {
		logger.info("Starting database...");

		//shutdown Derby when the program terminates
		//if the Dirby database is not shutdown, then changes to it will be lost
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				logger.info("Shutting down the database...");
				try {
					close();
				} catch (SQLException e) {
					logger.log(Level.SEVERE, "Error stopping database.", e);
				}
			}
		});

		//load the driver
		try {
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
		} catch (ClassNotFoundException e) {
			throw new SQLException("Database driver not on classpath.", e);
		}

		//create the connection
		if (create) {
			jdbcUrl += ";create=true";
		}
		db = DriverManager.getConnection(jdbcUrl);
		db.setAutoCommit(false); // default is true

		//create tables if database doesn't exist
		if (create) {
			logger.info("Database not found.  Creating the database...");
			String sql = null;
			SQLStatementReader in = null;
			String schemaFileName = "schema.sql";
			Statement statement = null;
			try {
				in = new SQLStatementReader(new InputStreamReader(ClasspathUtils.getResourceAsStream(schemaFileName, getClass())));
				statement = db.createStatement();
				while ((sql = in.readStatement()) != null) {
					statement.execute(sql);
				}
				sql = null;
				insertDbVersion(schemaVersion);
			} catch (IOException e) {
				throw new SQLException("Error creating database.", e);
			} catch (SQLException e) {
				if (sql == null){
					throw e;
				}
				throw new SQLException("Error executing SQL statement: " + sql, e);
			} finally {
				IOUtils.closeQuietly(in);
				if (statement != null) {
					try {
						statement.close();
					} catch (SQLException e) {
						//ignore
					}
				}
			}
			commit();
		} else {
			//update the database schema if it's not up to date
			int version = selectDbVersion();
			if (version < schemaVersion) {
				logger.info("Database schema out of date.  Upgrading from version " + version + " to " + schemaVersion + ".");
				String sql = null;
				Statement statement = null;
				try {
					statement = db.createStatement();
					while (version < schemaVersion) {
						logger.info("Performing schema update from version " + version + " to " + (version + 1) + ".");
						
						String script = "migrate-" + version + "-" + (version + 1) + ".sql";
						SQLStatementReader in = null;
						try{
							in = new SQLStatementReader(new InputStreamReader(ClasspathUtils.getResourceAsStream(script, getClass())));
							while ((sql = in.readStatement()) != null) {
								statement.execute(sql);
							}
							sql = null;
						} finally {
							IOUtils.closeQuietly(in);
						}
						
						version++;
					}
					updateDbVersion(schemaVersion);
				} catch (IOException e) {
					rollback();
					throw new SQLException("Error updating database schema.", e);
				} catch (SQLException e) {
					rollback();
					if (sql == null){
						throw e;
					}
					throw new SQLException("Error executing SQL statement during schema update: " + sql, e);
				} finally {
					if (statement != null) {
						try {
							statement.close();
						} catch (SQLException e) {
							//ignore
						}
					}
				}
				commit();
			}
		}
	}
	
	@Override
	public int selectDbVersion() throws SQLException {
		PreparedStatement statement = null;
		try {
			statement = db.prepareStatement("SELECT db_schema_version FROM sleet");
			ResultSet rs = statement.executeQuery();
			return rs.next() ? rs.getInt("db_schema_version") : 0;
		} finally {
			closeStatements(statement);
		}
	}
	
	@Override
	public void updateDbVersion(int version) throws SQLException {
		PreparedStatement statement = null;
		try {
			statement = db.prepareStatement("UPDATE sleet SET db_schema_version = ?");
			statement.setInt(1, version);
			statement.execute();
		} finally {
			closeStatements(statement);
		}
	}
	
	@Override
	public void insertDbVersion(int version) throws SQLException {
		PreparedStatement statement = null;
		try {
			statement = db.prepareStatement("INSERT INTO sleet (db_schema_version) VALUES (?)");
			statement.setInt(1, version);
			statement.execute();
		} finally {
			closeStatements(statement);
		}
	}

	@Override
	public void insertEmail(Email email) throws SQLException {
		PreparedStatement insertEmail = null;
		PreparedStatement insertRecipient = null;
		try {
			//insert the email
			insertEmail = db.prepareStatement("INSERT INTO emails (sender, data) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
			insertEmail.setString(1, email.sender.getAddress());
			insertEmail.setClob(2, new StringReader(email.data.toData()));
			insertEmail.execute();

			//get the value of row's generated ID
			ResultSet rs = insertEmail.getGeneratedKeys();
			rs.next();
			email.id = rs.getInt(1);

			//insert each recipient into the database
			insertRecipient = db.prepareStatement("INSERT INTO recipients (email_id, recipient) VALUES (?, ?)");
			insertRecipient.setInt(1, email.id);
			for (EmailAddress recipient : email.recipients) {
				insertRecipient.setString(2, recipient.getAddress());
				insertRecipient.execute();
			}
		} finally {
			closeStatements(insertEmail, insertRecipient);
		}
	}

	@Override
	public void insertInboxEmail(Email email) throws SQLException {
		//insert the email into the database
		if (email.id == null) {
			insertEmail(email);
		}

		PreparedStatement insertInbox = null;
		try {
			insertInbox = db.prepareStatement("INSERT INTO inbox_emails (email_id, received, user_id) VALUES (?, ?, ?)");
			insertInbox.setInt(1, email.id);
			insertInbox.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
			for (EmailAddress recipient : email.recipients) {
				String mailbox = recipient.getMailbox();
				Integer userId = selectUserId(mailbox);
				if (userId != null) {
					insertInbox.setInt(3, userId);
					insertInbox.execute();
				}
			}
		} finally {
			closeStatements(insertInbox);
		}
	}

	@Override
	public void insertOutboxEmail(Email email) throws SQLException {
		//insert the email into the database
		if (email.id == null) {
			insertEmail(email);
		}

		PreparedStatement insertOutbox = null;
		try {
			insertOutbox = db.prepareStatement("INSERT INTO outbox_emails (email_id, sent, user_id) VALUES (?, ?, ?)");
			insertOutbox.setInt(1, email.id);
			insertOutbox.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));

			String mailbox = email.sender.getMailbox();
			Integer userId = selectUserId(mailbox);
			if (userId != null) {
				insertOutbox.setInt(3, userId);
				insertOutbox.execute();
			}
		} finally {
			closeStatements(insertOutbox);
		}
	}

	private Integer selectUserId(String username) throws SQLException {
		//all usernames are stored in lower case
		username = username.toLowerCase();

		PreparedStatement userQuery = null;
		try {
			//check for a user with this name
			userQuery = db.prepareStatement("SELECT id FROM users WHERE username = ?");
			userQuery.setString(1, username);
			ResultSet rs = userQuery.executeQuery();
			return rs.next() ? rs.getInt("id") : null;
		} finally {
			closeStatements(userQuery);
		}
	}

	@Override
	public Email selectEmail(int emailId) throws IOException, SQLException {
		PreparedStatement selectEmail = null;
		PreparedStatement selectRecipients = null;
		try {
			Email email = null;
			selectEmail = db.prepareStatement("SELECT * FROM emails WHERE id = ?");
			selectEmail.setInt(1, emailId);
			selectRecipients = db.prepareStatement("SELECT recipient FROM recipients WHERE email_id = ?");
			ResultSet emailRs = selectEmail.executeQuery();
			if (emailRs.next()) {
				email = new Email();
				email.id = emailRs.getInt("id");
				email.sender = new EmailAddress(emailRs.getString("sender"));

				Clob clob = emailRs.getClob("data");
				email.data = new EmailData(IOUtils.toString(clob.getCharacterStream()));
				clob.free();

				selectRecipients.setInt(1, email.id);
				ResultSet recipRs = selectRecipients.executeQuery();
				while (recipRs.next()) {
					EmailAddress addr = new EmailAddress(recipRs.getString("recipient"));
					email.recipients.add(addr);
				}
			}
			return email;
		} finally {
			closeStatements(selectEmail, selectRecipients);
		}
	}

	@Override
	public List<Email> selectEmails() throws SQLException, IOException {
		final List<Email> emails = new ArrayList<Email>();

		foreachEmail(new EmailHandler() {
			@Override
			public void handleRow(Email email) {
				emails.add(email);
			}
		});

		return emails;
	}

	@Override
	public void foreachEmail(EmailHandler handler) throws SQLException, IOException {
		PreparedStatement selectEmails = null;
		PreparedStatement selectRecipients = null;
		try {
			selectEmails = db.prepareStatement("SELECT * FROM emails");
			selectRecipients = db.prepareStatement("SELECT recipient FROM recipients WHERE email_id = ?");
			ResultSet emailsRs = selectEmails.executeQuery();
			while (emailsRs.next()) {
				Email email = new Email();
				email.id = emailsRs.getInt("id");
				email.sender = new EmailAddress(emailsRs.getString("sender"));

				Clob clob = emailsRs.getClob("data");
				email.data = new EmailData(IOUtils.toString(clob.getCharacterStream()));
				clob.free();

				selectRecipients.setInt(1, email.id);
				ResultSet recipRs = selectRecipients.executeQuery();
				while (recipRs.next()) {
					EmailAddress addr = new EmailAddress(recipRs.getString("recipient"));
					email.recipients.add(addr);
				}

				handler.handleRow(email);
			}
		} finally {
			closeStatements(selectEmails, selectRecipients);
		}
	}

	@Override
	public List<POPEmail> selectEmailsForPOP(User user) throws SQLException {
		PreparedStatement selectEmails = null;
		try {
			selectEmails = db.prepareStatement("SELECT e.id, e.data FROM inbox_emails ie INNER JOIN emails e ON ie.email_id = e.id WHERE ie.user_id = ? ORDER BY ie.received DESC");
			selectEmails.setInt(1, user.id);
			ResultSet emailsRs = selectEmails.executeQuery();
			int popId = 1;
			List<POPEmail> emails = new ArrayList<POPEmail>();
			while (emailsRs.next()) {
				POPEmail email = new POPEmail();
				email.setDbId(emailsRs.getInt("id"));
				email.setPopId(popId);
				Clob body = emailsRs.getClob("data");
				email.setSize(body.length());
				body.free();
				emails.add(email);
				popId++;
			}
			return emails;
		} finally {
			closeStatements(selectEmails);
		}
	}

	@Override
	public void deleteInboxEmails(User user, List<Integer> emailIds) throws SQLException {
		PreparedStatement deleteEmails = null;
		try {
			deleteEmails = db.prepareStatement("DELETE FROM inbox_emails WHERE user_id = ? and email_id = ?");
			deleteEmails.setInt(1, user.id);
			for (Integer emailId : emailIds) {
				deleteEmails.setInt(2, emailId);
				deleteEmails.execute();
			}
		} finally {
			closeStatements(deleteEmails);
		}
	}

	@Override
	public String selectEmailData(int emailId) throws SQLException, IOException {
		PreparedStatement emailBody = null;
		try {
			emailBody = db.prepareStatement("SELECT data FROM emails WHERE id = ?");
			emailBody.setInt(1, emailId);
			ResultSet rs = emailBody.executeQuery();
			if (rs.next()) {
				Clob clob = rs.getClob("data");
				String body = IOUtils.toString(clob.getCharacterStream());
				clob.free();
				return body;
			}
			return null;
		} finally {
			closeStatements(emailBody);
		}
	}

	@Override
	public void foreachInboxEmail(User user, EmailHandler handler) throws SQLException, IOException {
		PreparedStatement selectEmails = null;
		PreparedStatement selectRecipients = null;
		try {
			selectEmails = db.prepareStatement("SELECT e.* FROM inbox_emails ie INNER JOIN emails e ON ie.email_id = e.id WHERE ie.user_id = ?");
			selectEmails.setInt(1, user.id);
			selectRecipients = db.prepareStatement("SELECT recipient FROM recipients WHERE email_id = ?");
			ResultSet emailsRs = selectEmails.executeQuery();
			while (emailsRs.next()) {
				Email email = new Email();
				email.id = emailsRs.getInt("id");
				email.sender = new EmailAddress(emailsRs.getString("sender"));

				Clob clob = emailsRs.getClob("data");
				email.data = new EmailData(IOUtils.toString(clob.getCharacterStream()));
				clob.free();

				selectRecipients.setInt(1, email.id);
				ResultSet recipRs = selectRecipients.executeQuery();
				while (recipRs.next()) {
					EmailAddress addr = new EmailAddress(recipRs.getString("recipient"));
					email.recipients.add(addr);
				}

				handler.handleRow(email);
			}
		} finally {
			closeStatements(selectEmails, selectRecipients);
		}
	}

	@Override
	public boolean doesMailboxExist(String mailbox) throws SQLException {
		//all mailbox names are stored in lower case
		mailbox = mailbox.toLowerCase();

		PreparedStatement userCount = null;
		PreparedStatement mailingListCount = null;
		try {
			boolean exists;

			//check for a user with this name
			userCount = db.prepareStatement("SELECT Count(*) FROM users WHERE username = ?");
			userCount.setString(1, mailbox);
			ResultSet rs = userCount.executeQuery();
			rs.next();
			if (rs.getInt(1) == 0) {
				//check for a mailing list with this name
				mailingListCount = db.prepareStatement("SELECT Count(*) FROM mailing_lists WHERE name = ?");
				mailingListCount.setString(1, mailbox);
				rs = mailingListCount.executeQuery();
				rs.next();
				exists = rs.getInt(1) > 0;
			} else {
				exists = true;
			}

			return exists;
		} finally {
			closeStatements(userCount, mailingListCount);
		}
	}

	@Override
	public User selectUser(String username) throws SQLException {
		//all mailbox names are stored in lower case
		username = username.toLowerCase();

		PreparedStatement userQuery = null;
		try {
			User user = null;
			userQuery = db.prepareStatement("SELECT * FROM users WHERE username = ?");
			userQuery.setString(1, username);
			ResultSet rs = userQuery.executeQuery();
			if (rs.next()) {
				user = new User();
				user.id = rs.getInt("id");
				user.username = rs.getString("username");
				user.password = rs.getString("password");
				user.fullName = rs.getString("full_name");
			}
			return user;
		} finally {
			closeStatements(userQuery);
		}
	}

	@Override
	public User selectUser(String username, String password) throws SQLException {
		//all mailbox names are stored in lower case
		username = username.toLowerCase();

		//password is stored in md5 in the database
		//password = DigestUtils.md5Hex(password);

		PreparedStatement userQuery = null;
		try {
			User user = null;
			userQuery = db.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?");
			userQuery.setString(1, username);
			userQuery.setString(2, password);
			ResultSet rs = userQuery.executeQuery();
			if (rs.next()) {
				user = new User();
				user.id = rs.getInt("id");
				user.username = rs.getString("username");
				user.password = rs.getString("password");
				user.fullName = rs.getString("full_name");
			}
			return user;
		} finally {
			closeStatements(userQuery);
		}
	}

	@Override
	public List<User> findUsers(String text) throws SQLException {
		//all mailbox names are stored in lower case
		text = text.toLowerCase();

		List<User> users = new ArrayList<User>();
		PreparedStatement userQuery = null;
		try {
			userQuery = db.prepareStatement("SELECT * FROM users WHERE username LIKE ? OR Lower(full_name) LIKE ?"); //username is already stored in lowercase in the database
			userQuery.setString(1, "%" + text + "%");
			userQuery.setString(2, "%" + text + "%");
			ResultSet rs = userQuery.executeQuery();
			while (rs.next()) {
				User user = null;
				user = new User();
				user.id = rs.getInt("id");
				user.username = rs.getString("username");
				user.password = rs.getString("password");
				user.fullName = rs.getString("full_name");
				users.add(user);
			}
			return users;
		} finally {
			closeStatements(userQuery);
		}
	}

	@Override
	public void insertUser(User user) throws SQLException {
		PreparedStatement insertUser = null;
		try {
			insertUser = db.prepareStatement("INSERT INTO users (username, password, full_name) VALUES (?, ?, ?)");
			insertUser.setString(1, user.username);
			insertUser.setString(2, user.password);
			insertUser.setString(3, user.fullName);
			insertUser.execute();
		} finally {
			closeStatements(insertUser);
		}
	}

	@Override
	public void updateUser(User user) throws SQLException {
		PreparedStatement updateUser = null;
		try {
			updateUser = db.prepareStatement("UPDATE users SET username = ?, password = ?, full_name = ? WHERE id = ?");
			updateUser.setString(1, user.username);
			updateUser.setString(2, user.password);
			updateUser.setString(3, user.fullName);
			updateUser.setInt(4, user.id);
			updateUser.execute();
		} finally {
			closeStatements(updateUser);
		}
	}

	@Override
	public void deleteUser(String username) throws SQLException {
		PreparedStatement deleteUser = null;
		try {
			deleteUser = db.prepareStatement("DELETE FROM users WHERE username = ?");
			deleteUser.setString(1, username);
			deleteUser.execute();
		} finally {
			closeStatements(deleteUser);
		}
	}

	@Override
	public List<User> selectUsers() throws SQLException {
		PreparedStatement selectUsers = null;
		List<User> users = new ArrayList<User>();
		try {
			selectUsers = db.prepareStatement("SELECT * FROM users ORDER BY username");
			ResultSet rs = selectUsers.executeQuery();
			while (rs.next()) {
				User user = new User();
				user.id = rs.getInt("id");
				user.username = rs.getString("username");
				user.password = rs.getString("password");
				user.fullName = rs.getString("full_name");
				users.add(user);
			}
			return users;
		} finally {
			closeStatements(selectUsers);
		}
	}

	@Override
	public MailingList selectMailingList(String name) throws SQLException {
		//all mailbox names are stored in lower case
		name = name.toLowerCase();

		PreparedStatement mailingListQuery = null;
		PreparedStatement userQuery = null;
		try {
			MailingList mailingList = null;

			mailingListQuery = db.prepareStatement("SELECT * FROM mailing_lists WHERE name = ?");
			mailingListQuery.setString(1, name);
			ResultSet rs = mailingListQuery.executeQuery();
			if (rs.next()) {
				mailingList = new MailingList();
				mailingList.id = rs.getInt("id");
				mailingList.name = rs.getString("name");

				//get users of the mailing list
				userQuery = db.prepareStatement("SELECT * FROM mailing_list_addresses WHERE mailing_list_id = ? ORDER BY address");
				userQuery.setInt(1, mailingList.id);
				ResultSet rs2 = userQuery.executeQuery();
				while (rs2.next()) {
					MailingListAddress address = new MailingListAddress();
					address.id = rs2.getInt("id");
					address.address = rs2.getString("address");
					address.name = rs2.getString("name");
					mailingList.addresses.add(address);
				}
			}

			return mailingList;
		} finally {
			closeStatements(mailingListQuery, userQuery);
		}
	}

	//	@Override
	//	public Map<String, List<OutboundEmailGroup>> selectOutboundEmailGroups() throws SQLException, IOException {
	//		PreparedStatement groupQuery = null;
	//		PreparedStatement recipientsQuery = null;
	//		PreparedStatement emailQuery = null;
	//		PreparedStatement failuresQuery = null;
	//		try {
	//			Map<Integer, Email> emails = new HashMap<Integer, Email>();
	//			Map<String, List<OutboundEmailGroup>> groups = new HashMap<String, List<OutboundEmailGroup>>();
	//			groupQuery = db.prepareStatement("SELECT * FROM outbound_email_groups");
	//			recipientsQuery = db.prepareStatement("SELECT recipient FROM outbound_email_recipients WHERE outbound_email_group_id = ?");
	//			failuresQuery = db.prepareStatement("SELECT error FROM outbound_email_failures WHERE outbound_email_group_id = ? ORDER BY id");
	//			emailQuery = db.prepareStatement("SELECT * FROM emails WHERE id = ?");
	//			ResultSet groupRs = groupQuery.executeQuery();
	//			while (groupRs.next()) {
	//				OutboundEmailGroup group = new OutboundEmailGroup();
	//
	//				group.id = groupRs.getInt("id");
	//				group.host = groupRs.getString("host");
	//				group.attempts = groupRs.getInt("attempts");
	//				group.firstAttempt = groupRs.getTimestamp("first_attempt");
	//				group.prevAttempt = groupRs.getTimestamp("prev_attempt");
	//
	//				//get recipients
	//				recipientsQuery.setInt(1, group.id);
	//				ResultSet rs2 = recipientsQuery.executeQuery();
	//				while (rs2.next()) {
	//					group.recipients.add(new EmailAddress(rs2.getString("recipient")));
	//				}
	//
	//				//get failures
	//				failuresQuery.setInt(1, group.id);
	//				rs2 = failuresQuery.executeQuery();
	//				while (rs2.next()) {
	//					group.failures.add(rs2.getString("error"));
	//				}
	//
	//				//get email
	//				int emailId = groupRs.getInt("email_id");
	//				Email email = emails.get(emailId);
	//				if (email == null) {
	//					email = selectEmail(emailId);
	//				}
	//				group.email = email;
	//
	//				List<OutboundEmailGroup> list = groups.get(group.host);
	//				if (list == null) {
	//					list = new LinkedList<OutboundEmailGroup>();
	//					groups.put(group.host, list);
	//				}
	//				list.add(group);
	//			}
	//			return groups;
	//		} finally {
	//			closeStatements(groupQuery, recipientsQuery, emailQuery, failuresQuery);
	//		}
	//	}

	@Override
	public Map<String, List<OutboundEmailGroup>> selectOutboundEmailGroupsToSend(long transientRetryInterval, long retryInterval) throws SQLException, IOException {
		PreparedStatement groupQuery = null;
		PreparedStatement recipientsQuery = null;
		PreparedStatement emailQuery = null;
		PreparedStatement failuresQuery = null;
		try {
			Map<Integer, Email> emails = new HashMap<Integer, Email>();
			Map<String, List<OutboundEmailGroup>> groups = new HashMap<String, List<OutboundEmailGroup>>();

			groupQuery = db.prepareStatement("SELECT * FROM outbound_email_groups WHERE attempts = 0 OR (attempts < 3 AND prev_attempt < ?) OR (attempts >= 3 AND prev_attempt < ?)");
			Timestamp ts = new Timestamp(System.currentTimeMillis() - transientRetryInterval);
			groupQuery.setTimestamp(1, ts);
			ts = new Timestamp(System.currentTimeMillis() - retryInterval);
			groupQuery.setTimestamp(2, ts);

			recipientsQuery = db.prepareStatement("SELECT recipient FROM outbound_email_recipients WHERE outbound_email_group_id = ?");
			failuresQuery = db.prepareStatement("SELECT error FROM outbound_email_failures WHERE outbound_email_group_id = ? ORDER BY id");
			emailQuery = db.prepareStatement("SELECT * FROM emails WHERE id = ?");
			ResultSet groupRs = groupQuery.executeQuery();
			while (groupRs.next()) {
				OutboundEmailGroup group = new OutboundEmailGroup();

				group.id = groupRs.getInt("id");
				group.host = groupRs.getString("host");
				group.attempts = groupRs.getInt("attempts");
				group.firstAttempt = groupRs.getTimestamp("first_attempt");
				group.prevAttempt = groupRs.getTimestamp("prev_attempt");

				//get recipients
				recipientsQuery.setInt(1, group.id);
				ResultSet rs2 = recipientsQuery.executeQuery();
				while (rs2.next()) {
					group.recipients.add(new EmailAddress(rs2.getString("recipient")));
				}

				//get failures
				failuresQuery.setInt(1, group.id);
				rs2 = failuresQuery.executeQuery();
				while (rs2.next()) {
					group.failures.add(rs2.getString("error"));
				}

				//get email
				int emailId = groupRs.getInt("email_id");
				Email email = emails.get(emailId);
				if (email == null) {
					email = selectEmail(emailId);
				}
				group.email = email;

				List<OutboundEmailGroup> list = groups.get(group.host);
				if (list == null) {
					list = new LinkedList<OutboundEmailGroup>();
					groups.put(group.host, list);
				}
				list.add(group);
			}
			return groups;
		} finally {
			closeStatements(groupQuery, recipientsQuery, emailQuery, failuresQuery);
		}
	}

	@Override
	public void deleteEmail(Email email) throws SQLException {
		PreparedStatement deleteQuery = null;
		try {
			deleteQuery = db.prepareStatement("DELETE FROM emails WHERE email_id = ?");
			deleteQuery.setInt(1, email.id);
			deleteQuery.execute();
		} finally {
			closeStatements(deleteQuery);
		}
	}

	@Override
	public void updateOutboundEmailGroup(OutboundEmailGroup group) throws SQLException {
		PreparedStatement updateQuery = null;
		PreparedStatement countFailuresQuery = null;
		PreparedStatement insertFailureQuery = null;

		try {
			updateQuery = db.prepareStatement("UPDATE outbound_email_groups SET attempts = ?, prev_attempt = ? WHERE id = ?");
			updateQuery.setInt(1, group.attempts);
			updateQuery.setTimestamp(2, new Timestamp(group.prevAttempt.getTime()));
			updateQuery.setInt(3, group.id);
			updateQuery.execute();

			countFailuresQuery = db.prepareStatement("SELECT Count(*) FROM outbound_email_failures WHERE outbound_email_group_id = ?");
			countFailuresQuery.setInt(1, group.id);
			ResultSet rs = countFailuresQuery.executeQuery();
			rs.next();
			int count = rs.getInt(1);
			if (count < group.failures.size()) {
				insertFailureQuery = db.prepareStatement("INSERT INTO outbound_email_failures (outbound_email_group_id, error) VALUES (?, ?)");
				insertFailureQuery.setInt(1, group.id);
				while (count < group.failures.size()) {
					insertFailureQuery.setString(2, group.failures.get(count));
					insertFailureQuery.execute();
					count++;
				}
			}
		} finally {
			closeStatements(updateQuery, countFailuresQuery, insertFailureQuery);
		}
	}

	@Override
	public void deleteOutboundEmailGroup(OutboundEmailGroup group) throws SQLException {
		PreparedStatement deleteQuery = null;
		try {
			deleteQuery = db.prepareStatement("DELETE FROM outbound_email_groups WHERE id = ?");
			deleteQuery.setInt(1, group.id);
			deleteQuery.execute();
		} finally {
			closeStatements(deleteQuery);
		}
	}

	@Override
	public void insertOutboundEmailGroup(OutboundEmailGroup group) throws SQLException {
		PreparedStatement insertGroup = null;
		PreparedStatement insertFailure = null;
		PreparedStatement insertRecipient = null;
		try {
			if (group.email.id == null) {
				insertEmail(group.email);
			}

			insertGroup = db.prepareStatement("INSERT INTO outbound_email_groups (email_id, host, attempts, first_attempt, prev_attempt) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			insertGroup.setInt(1, group.email.id);
			insertGroup.setString(2, group.host);
			insertGroup.setInt(3, group.attempts);
			insertGroup.setTimestamp(4, (group.firstAttempt == null) ? null : new Timestamp(group.firstAttempt.getTime()));
			insertGroup.setTimestamp(5, (group.prevAttempt == null) ? null : new Timestamp(group.prevAttempt.getTime()));
			insertGroup.execute();

			//get the value of row's generated ID
			ResultSet rs = insertGroup.getGeneratedKeys();
			rs.next();
			group.id = rs.getInt(1);

			//insert recipients
			insertRecipient = db.prepareStatement("INSERT INTO outbound_email_recipients (outbound_email_group_id, recipient) VALUES (?, ?)");
			insertRecipient.setInt(1, group.id);
			for (EmailAddress recipient : group.recipients) {
				insertRecipient.setString(2, recipient.getAddress());
				insertRecipient.execute();
			}

			//insert failures (but there probably aren't any because this group was just created)
			if (!group.failures.isEmpty()) {
				insertFailure = db.prepareStatement("INSERT INTO outbound_email_failures (outbound_email_group_id, error) VALUES (?, ?)");
				insertFailure.setInt(1, group.id);
				for (String failure : group.failures) {
					insertFailure.setString(2, failure);
					insertFailure.execute();
				}
			}
		} finally {
			closeStatements(insertGroup, insertFailure, insertRecipient);
		}
	}

	@Override
	public void commit() throws SQLException {
		db.commit();
	}

	@Override
	public void rollback() {
		try {
			db.rollback();
		} catch (SQLException e) {
			//exception is caught here instead of being thrown because ever time I catch the exception for rollback(), I just log the exception and continue on
			//plus, an exception is unlikely to be thrown here anyway
			logger.log(Level.WARNING, "Problem rolling back transaction.", e);
		}
	}

	@Override
	public void close() throws SQLException {
		try {
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		} catch (SQLException se) {
			if (se.getErrorCode() == 50000 && "XJ015".equals(se.getSQLState())) {
				// we got the expected exception
			} else if (se.getErrorCode() == 45000 && "08006".equals(se.getSQLState())) {
				// we got the expected exception for single database shutdown
			} else {
				// if the error code or SQLState is different, we have
				// an unexpected exception (shutdown failed)
				throw se;
			}
		}
	}

	/**
	 * Closes a list of Statements.
	 * @param statements the statements to close (nulls are ignored)
	 */
	private void closeStatements(Statement... statements) {
		for (Statement statement : statements) {
			if (statement != null) {
				try {
					statement.close();
				} catch (SQLException e) {
					//ignore
				}
			}
		}
	}
}
