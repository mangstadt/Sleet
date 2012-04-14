package sleet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.io.IOUtils;

/**
 * Records all communication that occurs in an SMTP transaction between the
 * client and server. This class does not need to be thread-safe because
 * client/server communication is synchronous.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class TransactionLog {
	private final StringBuilder log = new StringBuilder();
	private final DateFormat df = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
	private final Date started = new Date();

	/**
	 * Logs a client message.
	 * @param msg the message
	 */
	public void client(String msg) {
		log(true, msg);
	}

	/**
	 * Logs a server message.
	 * @param msg the message
	 */
	public void server(String msg) {
		log(false, msg);
	}

	/**
	 * Logs a message.
	 * @param client true to log a client message, false to log a server message
	 * @param msg the message to log
	 */
	private void log(boolean client, String msg) {
		String letter = client ? "C" : "S";

		Date now = new Date();
		log.append(letter + " [" + df.format(now) + "]: ");
		log.append(msg);
		if (!msg.endsWith("\n")){
			log.append('\n');
		}
	}

	/**
	 * Gets the entire transcript of the transaction.
	 * @return the entire transcript of the transaction
	 */
	@Override
	public String toString() {
		return log.toString();
	}
	
	/**
	 * Writes the transcript to a file.
	 * @param file the file to write to
	 * @throws IOException if there's a problem writing to the file
	 */
	public void writeToFile(File file) throws IOException{
		Date ended = new Date();
		
		long elapsed = ended.getTime() - started.getTime();
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(2);
		
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(file, true));
			writer.println("Started: " + df.format(started));
			writer.println("Ended: " + df.format(ended));
			writer.println("Time elapsed: " + nf.format(elapsed / 1000.0) + "s");
			writer.println(log.toString());
			writer.println("=====================\n");
		} finally {
			IOUtils.closeQuietly(writer);
		}
	}
}
