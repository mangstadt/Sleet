package sleet.email;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Date formatter for email dates.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
@SuppressWarnings("serial")
public class EmailDateFormat extends DateFormat {
	/**
	 * The preferred format.
	 */
	private final DateFormat longForm = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

	/**
	 * Day of the week is optional.
	 * @see RFC-5322 p.50
	 */
	private final DateFormat withoutDotw = new SimpleDateFormat("d MMM yyyy HH:mm:ss Z");

	/**
	 * Seconds and day of the week are optional.
	 * @see RFC-5322 p.49,50
	 */
	private final DateFormat withoutDotwSeconds = new SimpleDateFormat("d MMM yyyy HH:mm Z");

	/**
	 * Seconds are optional.
	 * @see RFC-5322 p.49
	 */
	private final DateFormat withoutSeconds = new SimpleDateFormat("EEE, d MMM yyyy HH:mm Z");

	/**
	 * Determines if a date string has the day of the week.
	 */
	private final Pattern dotwRegex = Pattern.compile("^[a-z]+,", Pattern.CASE_INSENSITIVE);

	/**
	 * Determines if a date string has seconds.
	 */
	private final Pattern secondsRegex = Pattern.compile("\\d{1,2}:\\d{2}:\\d{2}");

	/**
	 * Used for fixing obsolete two-digit years.
	 * @see RFC-5322, p.50
	 */
	private final Pattern twoDigitYearRegex = Pattern.compile("(\\d{1,2} [a-z]{3}) (\\d{2}) ", Pattern.CASE_INSENSITIVE);

	@Override
	public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
		return longForm.format(date, toAppendTo, fieldPosition);
	}

	@Override
	public Date parse(String source, ParsePosition pos) {
		//Note: it is assumed that comments are already removed because comments are not specific to dates

		//fix two-digit year
		Matcher m = twoDigitYearRegex.matcher(source);
		source = m.replaceFirst("$1 19$2 ");

		//remove extra whitespace
		//see RFC-5322, p.51
		source = source.replaceAll("\\s{2,}", " "); //remove runs of multiple whitespace chars
		source = source.replaceAll(" ,", ","); //remove any spaces before the comma that comes after the day of the week
		source = source.replaceAll("\\s*:\\s*", ":"); //remove whitespace around the colons in the time

		//is the day of the week included?
		m = dotwRegex.matcher(source);
		boolean dotw = m.find();

		//are seconds included?
		m = secondsRegex.matcher(source);
		boolean seconds = m.find();

		if (dotw && seconds) {
			return longForm.parse(source, pos);
		} else if (dotw) {
			return withoutSeconds.parse(source, pos);
		} else if (seconds) {
			return withoutDotw.parse(source, pos);
		} else {
			return withoutDotwSeconds.parse(source, pos);
		}
	}
}
