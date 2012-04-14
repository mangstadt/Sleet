package sleet.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Reads SQL statements one at a time from a .sql file, so they can be executed
 * via JDBC with Statement.execute().
 * @author mangst
 */
public class SQLStatementReader extends BufferedReader {
	public SQLStatementReader(Reader in) {
		super(in);
	}

	/**
	 * Reads the next SQL statement.
	 * @return the next SQL statement or null if EOF
	 * @throws IOException
	 */
	public String readStatement() throws IOException {
		StringBuilder sb = new StringBuilder();

		int i;
		while ((i = read()) != -1) {
			if (i == ';') {
				return sb.toString();
			}
			sb.append((char) i);
		}

		if (sb.length() == 0) {
			return null;
		}

		return sb.toString();
	}
}
