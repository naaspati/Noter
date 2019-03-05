package sam.noter;

import java.io.File;
import java.util.Objects;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import sam.fx.clipboard.FxClipboard;
import sam.fx.popup.FxPopupShop;
import sam.noter.dao.api.IEntry;

public interface Utils2 {

	public static void copyToClipboard(String s) {
		FxClipboard.setString(s);
		FxPopupShop.showHidePopup(s, 2000);
	}
	public static void fx(Runnable runnable) {
		Platform.runLater(runnable);
	}
	
	public enum FileChooserType {
		OPEN, SAVE
	}
	
	public static File chooseFile(Window parent, String title, File expectedDir, String expectedFilename, FileChooserType type) {
		Objects.requireNonNull(type);

		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));

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
	
	private final StringBuilder sb = new StringBuilder();
	
	public static String toTreeString(IEntry n) {
		sb.setLength(0);
		toTreeString(n, sb);
		sb.setLength(0);
		sb.toString();
	}
	public static void toTreeString(IEntry n, StringBuilder  sb) {
		toTreeString(n.getParent(), sb);
		
		if(n != null)
			sb.append(n.getTitle()).append(" > ");
	}
}
