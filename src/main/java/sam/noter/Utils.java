package sam.noter;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Logger;

import sam.myutils.ErrorRunnable;
import sam.myutils.System2;
import sam.nopkg.SavedAsStringResource;

@Deprecated
public class Utils {

	/* TODO
	 * private static final List<Runnable> onStop = new ArrayList<>();
	public static final Path APP_DATA = EnvKeys.APP_DATA;
	public static final Path TEMP_DIR = APP_DATA.resolve("java_temp");
	public static final int TEMP_DIR_COUNT = TEMP_DIR.getNameCount();
	
	 */
	static {
		String s = System2.lookup("session_file");
		if(s == null)
			System.setProperty("session_file", APP_DATA.resolve("session.properties").toString());
	}
	public static void init() {/*init static block */}

	private Utils() {}
	
	private static final SavedAsStringResource<File> last_visited = new SavedAsStringResource<>(APP_DATA.resolve("last-visited-folder.txt"), File::new);
	
	//for debug perposes
	public static String subpathWithPrefix(Path p) {
		if(p.getNameCount() > TEMP_DIR_COUNT && p.startsWith(TEMP_DIR))
			return "tempDir://"+p.subpath(TEMP_DIR_COUNT, p.getNameCount());
		return p.toString();
	}

	public static <E> E get(Logger logger, Callable<E> call, E defaultValue)  {
		try {
			return call.call();
		} catch (Exception e) {
			logger.catching(e);
		}
		return defaultValue;
	}
	public static boolean hide(Logger logger, ErrorRunnable call)  {
		try {
			call.run();
			return true;
		} catch (Exception e) {
			logger.catching(e);
			return false;
		}
	}
}
