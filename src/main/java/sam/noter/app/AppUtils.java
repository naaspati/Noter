package sam.noter.app;

import java.io.File;

import javafx.scene.Node;


public interface AppUtils {
	public enum FileChooserType {
		OPEN, SAVE
	}
	
	File chooseFile(String title, File expectedDir, String expectedFilename, FileChooserType type);
	Runnable showDialog(Node view);
}
