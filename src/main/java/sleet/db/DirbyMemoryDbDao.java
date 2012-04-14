package sleet.db;

import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class DirbyMemoryDbDao extends DirbyDbDao {
	public DirbyMemoryDbDao() throws SQLException {
		init("jdbc:derby:memory:sleet", true);
	}
}
