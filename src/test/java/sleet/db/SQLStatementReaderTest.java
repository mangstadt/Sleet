package sleet.db;

import java.io.StringReader;

import org.junit.Assert;
import org.junit.Test;

public class SQLStatementReaderTest {
	@Test
	public void test() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n);\ninsert into myTable (foo, bar) values (1, 'bar');");
		SQLStatementReader in = new SQLStatementReader(sr);
		Assert.assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		Assert.assertEquals("\ninsert into myTable (foo, bar) values (1, 'bar')", in.readStatement());
		Assert.assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void testOneStatementNoEndingSemicolon() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n)");
		SQLStatementReader in = new SQLStatementReader(sr);
		Assert.assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		Assert.assertNull(in.readStatement());
		in.close();
	}

	@Test
	public void testTwoStatementsNoEndingSemicolon() throws Exception {
		StringReader sr = new StringReader("create table myTable(\nfoo int,\nbar varchar(40)\n);\ninsert into myTable (foo, bar) values (1, 'bar')");
		SQLStatementReader in = new SQLStatementReader(sr);
		Assert.assertEquals("create table myTable(\nfoo int,\nbar varchar(40)\n)", in.readStatement());
		Assert.assertEquals("\ninsert into myTable (foo, bar) values (1, 'bar')", in.readStatement());
		Assert.assertNull(in.readStatement());
		in.close();
	}
}
