package sam.noter.app;

import javafx.scene.Node;

public interface FullScreen {
	Runnable fullscreen(Node node, Runnable onClose);
}
