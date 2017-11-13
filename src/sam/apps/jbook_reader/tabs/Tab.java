package sam.apps.jbook_reader.tabs;

import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import sam.apps.jbook_reader.datamaneger.DataManeger;

public class Tab extends DataManeger {
	private final HBox view = new HBox(5);
	private final Label title = new Label();
	private final Button close = new Button("x");

	public Tab(Path path, Consumer<Tab> onSelect) throws Exception {
		super(path);
		init(onSelect);
		setTitle(path.getFileName().toString());
	}

	public Tab(Consumer<Tab> onSelect) {
		super();
		init(onSelect);
	}
	private void init(Consumer<Tab> onSelect) {
		view.getChildren().addAll(title, close);
		title.setMaxWidth(70);
		HBox.setMargin(close, new Insets(2, 0, 0, 0) );

		setClass(view, "tab");
		setClass(title, "title");
		setClass(close, "close");
		view.setOnMouseClicked(e -> {
			if(e.isPrimaryButtonDown())
				onSelect.accept(this);
		});
	}

	public static void setClass(Node n, String...className) {
		ObservableList<String> l = n.getStyleClass();
		l.clear();
		l.addAll(className);
	}
	public HBox getView() { return view; }

	public void setTitle(String string) { title.setText(string); }
	public String getTitle() { return title.getText(); }
	public void setOnClose(Consumer<Tab> action) { close.setOnAction(e -> action.accept(Tab.this)); }

	@Override
	protected void setModified(boolean m) {
		super.setModified(m);
		if(isModified()) {
			if(!view.getStyleClass().contains("modified"))
				view.getStyleClass().add("modified");
		}
		else
			view.getStyleClass().remove("modified");
		
	}

	@Override
	public void setJbookPath(Path path) {
		setTitle(path.getFileName().toString());
		super.setJbookPath(path);
	}
	public boolean isActive() {
		return view.getStyleClass().contains("active");
	}
	public void setActive(boolean b) {
		if(b) {
			if(!view.getStyleClass().contains("active"))
				view.getStyleClass().add("active");
		}
		else
			view.getStyleClass().remove("active");
	}

	public void setContextMenu(ContextMenu cm) {
		view.setOnContextMenuRequested(e -> {
			cm.setUserData(Tab.this);
			cm.show(view, Side.BOTTOM, 0, 0);
		});
	}
}
