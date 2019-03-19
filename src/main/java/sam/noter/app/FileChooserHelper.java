package sam.noter.app;

import java.io.File;
import java.util.function.Consumer;

import javafx.stage.FileChooser.ExtensionFilter;


public interface FileChooserHelper {
	public static final ExtensionFilter JBOOK_FILTER = new ExtensionFilter("jbook file", "*.jbook"); 
	
	public enum Type {
		OPEN, SAVE
	}
	/**
	 * 
	 * @param title
	 * @param expectedDir
	 * @param expectedFilename
	 * @param type
	 * @param onSelect will be called of there is a selected file. i.e. return file != null
	 * @param filters
	 * @return selected file (could be null)
	 */
	File chooseFile(String title, File expectedDir, String expectedFilename, Type type, Consumer<File> onSelect, ExtensionFilter...filters);
}
