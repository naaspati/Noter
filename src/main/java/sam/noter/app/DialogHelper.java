package sam.noter.app;

import javafx.scene.Node;


public interface DialogHelper {
	/**
	 * 
	 * @param view
	 * @return a runnable, which when called closed the dialog
	 */
	Runnable showDialog(Node view);
}
