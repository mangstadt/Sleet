package sleet;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;

import org.junit.Test;

import sleet.db.DbDao;

/**
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class ClasspathUtilsTest {
	@Test
	public void getResourceAsStream() throws Exception {
		//without relative class
		//ClasspathUtils is used as the relative class if no relative class is defined
		try {
			ClasspathUtils.getResourceAsStream("Arguments.class");
		} catch (FileNotFoundException e) {
			fail();
		}
		try {
			ClasspathUtils.getResourceAsStream("does-not-exist");
			fail();
		} catch (FileNotFoundException e) {
			String msg = e.getMessage();
			assertEquals("File not found on classpath: sleet/does-not-exist", msg);
		}

		//with relative class
		try {
			ClasspathUtils.getResourceAsStream("schema.sql", DbDao.class);
		} catch (FileNotFoundException e) {
			fail();
		}

		try {
			ClasspathUtils.getResourceAsStream("does-not-exist", DbDao.class);
			fail();
		} catch (FileNotFoundException e) {
			String msg = e.getMessage();
			assertEquals("File not found on classpath: sleet/db/does-not-exist", msg);
		}

		//using absolute paths
		try {
			ClasspathUtils.getResourceAsStream("/sleet/db/schema.sql");
		} catch (FileNotFoundException e) {
			fail();
		}

		try {
			ClasspathUtils.getResourceAsStream("/sleet/db/does-not-exist");
			fail();
		} catch (FileNotFoundException e) {
			String msg = e.getMessage();
			assertEquals("File not found on classpath: sleet/db/does-not-exist", msg);
		}
	}
}
