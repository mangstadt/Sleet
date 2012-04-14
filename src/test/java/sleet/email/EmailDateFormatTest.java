package sleet.email;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;

public class EmailDateFormatTest {
	@Test
	public void parse() throws Exception {
		EmailDateFormat df = new EmailDateFormat();
		Calendar c;
		Date expected, actual;

		//+ day of the week
		//- seconds
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("Sun, 8 Apr 2012 10:25 -0400");
		assertEquals(expected, actual);

		//+ date of the week
		//+ seconds
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("Sun, 8 Apr 2012 10:25:01 -0400");
		assertEquals(expected, actual);

		//- day of the week
		//- seconds
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("8 Apr 2012 10:25 -0400");
		assertEquals(expected, actual);

		//- date of the week
		//+ seconds
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("8 Apr 2012 10:25:01 -0400");
		assertEquals(expected, actual);

		//single-digit date
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("Sun, 8 Apr 2012 10:25:01 -0400");
		assertEquals(expected, actual);

		//two-digit date
		c = Calendar.getInstance();
		c.set(2012, 3, 10, 14, 25, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("Tue, 10 Apr 2012 10:25 -0400");
		assertEquals(expected, actual);

		//obsolete timezone format (see RFC-5322, p.50)
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("Sun, 8 Apr 2012 10:25:01 EDT");
		assertEquals(expected, actual);
		
		//obsolete year format (see RFC-5322, p.50)
		c = Calendar.getInstance();
		c.set(1999, 3, 8, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("8 Apr 99 10:25:01 EDT");
		assertEquals(expected, actual);
		
		//with extra whitespacee
		c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		expected = c.getTime();
		actual = df.parse("Sun , 8   Apr 2012   10 :   25  -0400");
		assertEquals(expected, actual);
	}

	@Test
	public void format() throws Exception {
		EmailDateFormat df = new EmailDateFormat();

		//the long format should always be used

		//single-digit date
		Calendar c = Calendar.getInstance();
		c.set(2012, 3, 8, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		Date input = c.getTime();
		String expected = "Sun, 8 Apr 2012 10:25:01 -0400";
		String actual = df.format(input);
		assertEquals(expected, actual);

		//two-digit date
		c = Calendar.getInstance();
		c.set(2012, 3, 10, 14, 25, 1);
		c.set(Calendar.MILLISECOND, 0);
		c.setTimeZone(TimeZone.getTimeZone("-0400"));
		input = c.getTime();
		expected = "Tue, 10 Apr 2012 10:25:01 -0400";
		actual = df.format(input);
		assertEquals(expected, actual);
	}
}
