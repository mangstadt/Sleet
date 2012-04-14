CREATE TABLE users(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	username VARCHAR(100) NOT NULL,
	
	--password must be stored in plaintext in order for POP3 APOP command to work
	password VARCHAR(100) NOT NULL,
	
	full_name VARCHAR(100)
);
INSERT INTO users (username, full_name, password) VALUES ('postmaster', 'Postmaster', 'secret');
INSERT INTO users (username, full_name, password) VALUES ('test', NULL, 'very-secret');
INSERT INTO users (username, full_name, password) VALUES ('mike', 'Mike Angstadt', 'mike');

CREATE TABLE mailing_lists(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	name VARCHAR(100) NOT NULL
);
INSERT INTO mailing_lists (name) VALUES ('work');

CREATE TABLE mailing_list_addresses(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	mailing_list_id INTEGER NOT NULL REFERENCES mailing_lists(id),
	address VARCHAR(100) NOT NULL,
	name VARCHAR(100)
);
INSERT INTO mailing_list_addresses (mailing_list_id, address, name) VALUES (1, 'bob@yahoo.com', NULL);
INSERT INTO mailing_list_addresses (mailing_list_id, address, name) VALUES (1, 'david@hotmail.com', 'David Jones');

/*
 * All inbound and outbound emails.
 */
CREATE TABLE emails(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	sender VARCHAR(100) NOT NULL,
	data CLOB(1000 K) NOT NULL
);

/**
 * The recipients of each email (linked to the "emails" table).
 */
CREATE TABLE recipients(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	email_id INTEGER NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
	recipient VARCHAR(100)
);

/* The emails that each user received. */
CREATE TABLE inbox_emails(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	user_id INTEGER NOT NULL REFERENCES users(id),
	email_id INTEGER NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
	received TIMESTAMP NOT NULL
);

/* The emails that were successfully sent from the SMTP server. */
CREATE TABLE outbox_emails(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	user_id INTEGER NOT NULL REFERENCES users(id),
	email_id INTEGER NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
	sent TIMESTAMP NOT NULL
);

/*
 * The queue of emails that are waiting to be sent.
 * If there's a problem sending an email, it will stay in this queue and the server will try sending it again later.
 */
CREATE TABLE outbound_email_groups(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	email_id INTEGER NOT NULL REFERENCES emails(id) ON DELETE CASCADE,
	
	--the host that this email is destined for (e.g. "gmail.com")
	host VARCHAR(55) NOT NULL,
	
	--the number of times it tried to send this email
	attempts INT NOT NULL,
	
	--the time of the first attempt
	first_attempt TIMESTAMP,
	
	--the time of the previous attempt
	prev_attempt TIMESTAMP
);

CREATE TABLE outbound_email_recipients(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	outbound_email_group_id INTEGER NOT NULL REFERENCES outbound_email_groups(id) ON DELETE CASCADE,
	recipient VARCHAR(100) NOT NULL
);

/* The failures that occurred while attempting to send emails */
CREATE TABLE outbound_email_failures(
	id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	outbound_email_group_id INTEGER NOT NULL REFERENCES outbound_email_groups(id) ON DELETE CASCADE,
	error VARCHAR(2000) NOT NULL
);