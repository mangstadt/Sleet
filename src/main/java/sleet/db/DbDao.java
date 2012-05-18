package sleet.db;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Defines the data access object methods.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public interface DbDao {
	/**
	 * Gets the database schema version.
	 * @return the version number
	 * @throws SQLException
	 */
	int selectDbVersion() throws SQLException;

	/**
	 * Updates the database schema version.
	 * @param version the new version number
	 * @throws SQLException
	 */
	void updateDbVersion(int version) throws SQLException;

	/**
	 * Sets the database schema version, inserting the necessary row in the
	 * table.
	 * @param version the version number
	 * @throws SQLException
	 */
	void insertDbVersion(int version) throws SQLException;

	/**
	 * Inserts an email.
	 * @param email the email to insert
	 * @throws SQLException
	 */
	void insertEmail(Email email) throws SQLException;

	/**
	 * Adds the email to the inboxes of the users who are receiving the email.
	 * @param email the email that was received
	 * @throws SQLException
	 */
	void insertInboxEmail(Email email) throws SQLException;

	/**
	 * Adds the email to the outbox of the user who sent the email.
	 * @param email the email that was sent
	 * @throws SQLException
	 */
	void insertOutboxEmail(Email email) throws SQLException;

	/**
	 * Gets all emails.
	 * @return all emails
	 * @throws SQLException
	 * @throws IOException
	 */
	List<Email> selectEmails() throws SQLException, IOException;

	/**
	 * Iterates over each email.
	 * @param handler called for every email in the result set
	 * @throws SQLException
	 * @throws IOException
	 */
	void foreachEmail(EmailHandler handler) throws SQLException, IOException;

	/**
	 * Gets the emails in a user's inbox.
	 * @param user the user
	 * @param handler called for every email in the result set
	 * @throws SQLException
	 * @throws IOException
	 */
	void foreachInboxEmail(User user, EmailHandler handler) throws SQLException, IOException;

	/**
	 * Determines if a given mailbox exists on the server (checks for an
	 * existing user or an existing mailing list).
	 * @param mailbox the mailbox name
	 * @return true if the mailbox exists, false if not
	 * @throws SQLException
	 */
	boolean doesMailboxExist(String mailbox) throws SQLException;

	/**
	 * Gets a user's information using her username.
	 * @param username the user's username
	 * @return the user or null if not found
	 * @throws SQLException
	 */
	User selectUser(String username) throws SQLException;

	/**
	 * Gets a user's information using her username and password.
	 * @param username the user's username
	 * @param password the user's password (plaintext)
	 * @return the user or null if not found
	 * @throws SQLException
	 */
	User selectUser(String username, String password) throws SQLException;

	/**
	 * Searches for a user that has a username or real name similar to the given
	 * text.
	 * @param text the text to search for
	 * @return all users that match the search query
	 * @throws SQLException
	 */
	List<User> findUsers(String text) throws SQLException;

	void insertUser(User user) throws SQLException;

	void updateUser(User user) throws SQLException;

	void deleteUser(String username) throws SQLException;

	List<User> selectUsers() throws SQLException;

	/**
	 * Gets a mailing list.
	 * @param name the name of the mailing list
	 * @return the mailing list or null if not found
	 * @throws SQLException
	 */
	MailingList selectMailingList(String name) throws SQLException;

	/**
	 * Gets all the emails in the user's inbox for a POP3 session.
	 * @param user the user
	 * @return the user's inbox emails
	 * @throws SQLException
	 */
	List<POPEmail> selectEmailsForPOP(User user) throws SQLException;

	/**
	 * Deletes emails from the user's inbox.
	 * @param user the user
	 * @param emailIds the IDs of the emails to delete
	 * @throws SQLException
	 */
	void deleteInboxEmails(User user, List<Integer> emailIds) throws SQLException;

	/**
	 * Gets the body of an email.
	 * @param emailId the email ID
	 * @return the email's body or null if no email found
	 * @throws SQLException
	 * @throws IOException
	 */
	String selectEmailData(int emailId) throws SQLException, IOException;

	Email selectEmail(int emailId) throws SQLException, IOException;

	void deleteEmail(Email email) throws SQLException;

	//Map<String, List<OutboundEmailGroup>> selectOutboundEmailGroups() throws SQLException, IOException;

	/**
	 * Selects the outbound email groups that should be sent.
	 * @param transientRetryInterval
	 * @param retryInterval
	 * @return key = host name, value = outbound email groups that have
	 * recipients belonging to that host
	 * @throws SQLException
	 * @throws IOException
	 */
	Map<String, List<OutboundEmailGroup>> selectOutboundEmailGroupsToSend(long transientRetryInterval, long retryInterval) throws SQLException, IOException;

	void updateOutboundEmailGroup(OutboundEmailGroup group) throws SQLException;

	void deleteOutboundEmailGroup(OutboundEmailGroup group) throws SQLException;

	void insertOutboundEmailGroup(OutboundEmailGroup group) throws SQLException;

	/**
	 * Commits the current database transaction.
	 * @throws SQLException
	 */
	void commit() throws SQLException;

	/**
	 * Rollsback the current database transaction.
	 */
	void rollback();

	/**
	 * Closes the database connection.
	 * @throws SQLException
	 */
	void close() throws SQLException;

	interface EmailHandler {
		/**
		 * Defines how to handle each email in the result set.
		 * @param email the email
		 */
		void handleRow(Email email);
	}
}
