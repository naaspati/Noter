package sam.noter;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.Window;
import sam.config.Session;
import sam.fx.clipboard.FxClipboard;
import sam.fx.popup.FxPopupShop;
import sam.myutils.ErrorRunnable;
import sam.myutils.System2;
import sam.nopkg.SavedAsStringResource;
import sam.noter.dao.Entry;

public class Utils {

	private static final List<Runnable> onStop = new ArrayList<>();
	public static final Path APP_DATA = EnvKeys.APP_DATA;
	public static final Path TEMP_DIR = APP_DATA.resolve("java_temp");
	public static final int TEMP_DIR_COUNT = TEMP_DIR.getNameCount();
	
	static {
		String s = System2.lookup("session_file");
		if(s == null)
			System.setProperty("session_file", APP_DATA.resolve("session.properties").toString());
	}
	public static void init() {/*init static block */}

	private Utils() {}

	public enum FileChooserType {
		OPEN, SAVE
	}
	private static final SavedAsStringResource<File> last_visited = new SavedAsStringResource<>(APP_DATA.resolve("last-visited-folder.txt"), File::new);

	public static File chooseFile(String title, File expectedDir, String expectedFilename, FileChooserType type) {
		Objects.requireNonNull(type);

		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));
		Window parent = Session.global().get(Stage.class);

		if(expectedDir == null || !expectedDir.isDirectory())
			expectedDir = last_visited.get();

		if(expectedDir != null && expectedDir.isDirectory())
			chooser.setInitialDirectory(expectedDir);

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == FileChooserType.OPEN ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);

		if(file != null) 
			last_visited.set(file.getParentFile());
		return file;
	}
	public static Entry castEntry(TreeItem<String> parent) {
		return (Entry)parent;
	}
	public static void addOnStop(Runnable action) {
		onStop.add(action);
	}
	public static void stop() {
		onStop.forEach(Runnable::run);
	}

	public static void copyToClipboard(String s) {
		FxClipboard.setString(s);
		FxPopupShop.showHidePopup(s, 2000);
	}
	public static void fx(Runnable runnable) {
		Platform.runLater(runnable);
	}
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
