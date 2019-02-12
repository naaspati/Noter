package sam.noter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
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
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.myutils.System2;
import sam.noter.dao.Entry;

public class Utils {
	private static final Logger logger = LogManager.getLogger(Utils.class);

	private static final List<Runnable> onStop = new ArrayList<>();
	public static final Path APP_DATA = EnvKeys.APP_DATA;
	public static final Path TEMP_DIR = APP_DATA.resolve("java_temp");

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
	private static final Path last_visited_save = APP_DATA.resolve("last-visited-folder.txt");
	private static File last_visited;
	
	public static File chooseFile(String title, File expectedDir, String expectedFilename, FileChooserType type) {
		Objects.requireNonNull(type);

		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));
		Window parent = Session.global().get(Stage.class);

		if(expectedDir == null || !expectedDir.isDirectory()){
			if(last_visited != null)
				expectedDir = last_visited;
			else {
				try {
					expectedDir = Files.exists(last_visited_save) ? new File(StringReader2.getText(last_visited_save)) : null;
				} catch (IOException e) {
					logger.error("failed to read: {}", last_visited_save, e);
					expectedDir = null;
				}
			}
		}

		if(expectedDir != null && expectedDir.isDirectory())
			chooser.setInitialDirectory(expectedDir);

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == FileChooserType.OPEN ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);

		if(file != null) {
			try {
				last_visited = file.getParentFile();
				StringWriter2.setText(last_visited_save, last_visited.toString().replace('\\', '/'));
			} catch (IOException e) {}
		}
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
}
