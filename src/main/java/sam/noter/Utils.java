package sam.noter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.Window;
import sam.config.Session;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.myutils.System2;
import sam.noter.dao.Entry;

public class Utils {
	private static final Logger LOGGER = MyLoggerFactory.logger(Utils.class);

	private static final List<Runnable> onStop = new ArrayList<>();
	public static final Path APP_DATA = Optional.ofNullable(System2.lookup("app_data")).map(Paths::get).orElse(Paths.get("app_data"));
	public static final Path BACKUP_DIR = Optional.ofNullable(System2.lookup("backup_dir")).map(Paths::get).orElse(APP_DATA.resolve("java_temp"));
	

	private Utils() {}

	public enum FileChooserType {
		OPEN, SAVE
	}

	public static Path chooseFile(String title, Path expectedDir, String expectedFilename, FileChooserType type) {
		Objects.requireNonNull(type);

		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));
		Window parent = Session.get(Stage.class);

		if(expectedDir == null || !Files.isDirectory(expectedDir)){
			final Path p = Utils.APP_DATA.resolve("last-visited-folder.txt");
			try {
				expectedDir = Files.exists(p) ? Paths.get(StringReader2.getText(p)) : null;
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "failed to read: "+p, e);
				expectedDir = null;
			}
		}

		if(expectedDir != null && Files.isDirectory(expectedDir))
			chooser.setInitialDirectory(expectedDir.toFile());

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == FileChooserType.OPEN ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);

		if(file != null) {
			final Path p = Utils.APP_DATA.resolve("last-visited-folder.txt");
			try {
				StringWriter2.setText(p, file.getParent().toString().replace('\\', '/'));
			} catch (IOException e) {}
			return file.toPath();
		}

		return null;
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
}
