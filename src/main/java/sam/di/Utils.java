package sam.di;

import java.io.File;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import sam.noter.tabs.Tab;


public interface Utils {
	public enum FileChooserType {
		OPEN, SAVE
	}
	
	File chooseFile(String title, File expectedDir, String expectedFilename, FileChooserType type);

	Runnable showDialog(Node view);
	ObservableValue<Tab> currentTabProperty();
}
