package sleet.db;

import java.io.File;
import java.sql.SQLException;

/**
 * Data access object implementation for embedded Derby database.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class DirbyEmbeddedDbDao extends DirbyDbDao {
	/**
	 * @param databaseDir the directory the database will be saved to
	 * @throws SQLException
	 */
	public DirbyEmbeddedDbDao(File databaseDir) throws SQLException {
		databaseDir = new File(databaseDir.getAbsolutePath());
		System.setProperty("derby.system.home", databaseDir.getParentFile().getAbsolutePath());
		init("jdbc:derby:" + databaseDir.getName(), !databaseDir.isDirectory());
	}
}
