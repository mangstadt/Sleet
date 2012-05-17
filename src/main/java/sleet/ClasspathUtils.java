package sleet;

import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * Contains classpath-related utility methods.
 * @author Mike Angstadt [mike.angstadt@gmail.com]
 */
public class ClasspathUtils {
	/**
	 * The same as {@link Class#getResourceAsStream(String)}, except throws a
	 * {@link FileNotFoundException} when null is returned.
	 * @param file the file name of the resource
	 * @param relativeLocation the class to use as the base location
	 * @return an input stream to the resource
	 * @throws FileNotFoundException if the resource does not exist
	 */
	public static InputStream getResourceAsStream(String file, Class<?> relativeLocation) throws FileNotFoundException {
		InputStream in = relativeLocation.getResourceAsStream(file);
		if (in == null) {
			String path;
			if (file.startsWith("/")) {
				path = file.substring(1); //remove starting slash so it's the same as Package.getName()
			} else {
				path = relativeLocation.getPackage().getName().replaceAll("\\.", "/");
				path += "/" + file;
			}
			throw new FileNotFoundException("File not found on classpath: " + path);
		}
		return in;
	}

	/**
	 * The same as {@link Class#getResourceAsStream(String)}, except throws a
	 * {@link FileNotFoundException} when null is returned.
	 * @param absolutePath the absolute path to the resource
	 * @return an input stream to the resource
	 * @throws FileNotFoundException if the resource does not exist
	 */
	public static InputStream getResourceAsStream(String absolutePath) throws FileNotFoundException {
		return getResourceAsStream(absolutePath, ClasspathUtils.class);
	}
}
