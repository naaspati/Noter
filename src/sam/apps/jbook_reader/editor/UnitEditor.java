package sam.apps.jbook_reader.editor;

import static sam.apps.jbook_reader.Utils.addClass;
import static sam.apps.jbook_reader.Utils.button;
import static sam.apps.jbook_reader.Utils.toggleClass;

import java.util.function.Consumer;

import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import sam.apps.jbook_reader.Utils;
import sam.apps.jbook_reader.tabs.Tab;

final class UnitEditor extends BorderPane {
	private final Text title;
	private final TextArea content;
	private final Tab tab; 
	private final TreeItem<String> item;
	private boolean editStarted;
	private final Button editButton;
	private WeakChangeListener<String> listener;

	public UnitEditor(Tab tab, TreeItem<String> item, 
			Consumer<UnitEditor> onSelected) {
		
		title = new Text(tab.getTitle(item));
		String text = tab.getContent(item);
		content = new TextArea(text);
		content.setEditable(false);
		updateFont();
		
		long count = text == null || text.isEmpty() ? 0 : text.chars().filter(s -> s == '\n').count();
		content.setPrefRowCount(count  < 5 ? 5 : (count > 40 ? 40 : (int)count));

		this.tab = tab;
		this.item = item;
		
		Pane p;
		HBox titleContainer = new HBox(title, p = new Pane(), editButton = button("edit", "edit.png", e -> onSelected.accept(this))); 
		setTop(titleContainer);
		titleContainer.setPadding(new Insets(5, 10, 5, 10));
		setCenter(content);
		
		HBox.setHgrow(p, Priority.ALWAYS);
		
		addClass(this, "unit-editor");
		addClass(titleContainer, "title-box");
		addClass(title, "title");
		addClass(content, "content");
		
		listener = new WeakChangeListener<>((prop, old, nnew) -> {
			editStarted = true;
			listener = null;
		});
		
		content.textProperty().addListener(listener);
	}

	public void updateFont() {
		title.setFont(Editor.getFont());
		content.setFont(Editor.getFont());
	}
	void finish() {
		if(!editStarted)
			return;
		
		tab.setTitle(item, title.getText());
		tab.setContent(item, content.getText());
	}
	public boolean isActive() {
		return getStyleClass().contains("active");
	}
	public void setActive(boolean active) {
		title.setText(active ? Utils.treeToString(item) : tab.getTitle(item));
		toggleClass(this, "active", active);
		editButton.setVisible(!active);
		content.setEditable(active);
	}
	public String getItemTitle() {
		return tab.getTitle(item);
	}
	public TreeItem<String> getItem() {
		return item;
	}

	public void setWordWrap(boolean wrap) {
		content.setWrapText(wrap);
	}
}
