package sam.apps.jbook_reader.tabs;

import static sam.apps.jbook_reader.Utils.setClass;
import static sam.apps.jbook_reader.Utils.toggleClass;

import java.nio.file.Path;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import sam.apps.jbook_reader.datamaneger.DataManeger;

public class Tab extends DataManeger {
	private final HBox view = new HBox(5);
	private final Label title = new Label();
	private final Button close = new Button("x");

	public Tab(Path path, Consumer<Tab> onSelect) throws Exception {
		super(path);
		init(onSelect);
		setTabTitle(path.getFileName().toString());
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
			if(e.getButton() == MouseButton.PRIMARY)
				onSelect.accept(this);
		});
	}
	
	public HBox getView() { return view; }

	public void setTabTitle(String string) { title.setText(string); }
	public String getTabTitle() { return title.getText(); }
	public void setOnClose(Consumer<Tab> action) { close.setOnAction(e -> action.accept(Tab.this)); }

	@Override
	public void setModified(boolean m) {
		super.setModified(m);
		toggleClass(view, "modified", isModified());
	}

	@Override
	public void setJbookPath(Path path) {
		setTabTitle(path.getFileName().toString());
		super.setJbookPath(path);
	}
	public boolean isActive() {
		return view.getStyleClass().contains("active");
	}
	public void setActive(boolean b) {
		toggleClass(view, "active", b);
	}
	public void setContextMenu(ContextMenu cm) {
		view.setOnContextMenuRequested(e -> {
			cm.setUserData(Tab.this);
			cm.show(view, Side.BOTTOM, 0, 0);
		});
	}
}
