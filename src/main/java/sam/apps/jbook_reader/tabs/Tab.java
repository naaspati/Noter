package sam.apps.jbook_reader.tabs;

import static sam.fx.helpers.FxClassHelper.setClass;
import static sam.fx.helpers.FxClassHelper.toggleClass;

import java.io.File;
import java.util.function.Consumer;

import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import sam.apps.jbook_reader.datamaneger.DataManeger;
import sam.io.fileutils.FileOpenerNE;

public class Tab extends DataManeger {
	private final HBox view = new HBox(5);
	private final Label title = new Label();
	private final Button close = new Button("x");
	private final Button open = new Button("o");

	public Tab(File path, Consumer<Tab> onSelect) throws Exception {
		super(path);
		init(onSelect);
		setTabTitle(path.getName());
	}

	public Tab(Consumer<Tab> onSelect) throws Exception {
		super();
		init(onSelect);
	}
	private void init(Consumer<Tab> onSelect) {
		view.getChildren().addAll(title, close);
		title.setMaxWidth(70);

		setClass(view, "tab");
		setClass(title, "title");
		setClass(close, "close");
		setClass(open, "open");
		
		open.setOnAction(e -> FileOpenerNE.openFile((File)open.getUserData()));
		close.setTooltip(new Tooltip("close tab"));
		
		view.setOnMouseClicked(e -> {
			if(e.getButton() == MouseButton.PRIMARY)
				onSelect.accept(this);
		});
	}
	
	public HBox getView() { return view; }

	public void setTabTitle(String string) { 
		title.setText(string);
		title.setTooltip(new Tooltip(string));
		}
	public String getTabTitle() { return title.getText(); }
	public void setOnClose(Consumer<Tab> action) { close.setOnAction(e -> action.accept(Tab.this)); }

	@Override
	protected void setModified(boolean b) {
		super.setModified(b);
		if(view == null)
			return;
		toggleClass(view, "modified", isModified());
	}
	@Override
	public void setJbookPath(File path) {
		setTabTitle(path.getName());
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
	public void setBoundBook(File file) {
		if(file == null) 
			view.getChildren().remove(open);
		else {
			open.setTooltip(new Tooltip("open bound book: "+file.getName()));
			open.setUserData(file);
			if(!view.getChildren().contains(open))
				view.getChildren().add(open);
		}
	}
}
