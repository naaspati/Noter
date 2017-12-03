package sam.apps.jbook_reader.editor;

import static sam.fx.helpers.FxHelpers.addClass;
import static sam.fx.helpers.FxHelpers.button;

import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import sam.apps.jbook_reader.Utils;
import sam.apps.jbook_reader.datamaneger.Entry;

class UnitEditor extends BorderPane {
	protected final Label title = new Label();
	private final TextArea content = new TextArea();
	protected Entry item;
	private volatile boolean tabItemChanging = false;

	public UnitEditor(Consumer<Entry> onExpanded) {
		updateFont();
		setCenter(content);

		if(onExpanded != null) {
			Button expandButton = button("edit", null, e -> onExpanded.accept(this.item));
			addClass(expandButton, "expand-button");

			Pane p = new Pane();
			HBox titleContainer = new HBox(title, p, expandButton);
			titleContainer.setPadding(new Insets(5, 10, 5, 10));

			HBox.setHgrow(p, Priority.ALWAYS);

			addClass(titleContainer, "title-box");
			setTop(titleContainer);
		}
		else {
			setTop(title);
			addClass(title, "title");
			title.setMaxWidth(Double.MAX_VALUE);
		}

		addClass(this, "unit-editor");
		addClass(content, "content");

		content.textProperty().addListener((prop, old, _new) -> {
			if(!tabItemChanging)
				item.setContent(_new);
		});
	}

	public void setItem(Entry item) {
		tabItemChanging = true;
		this.item = item;
		title.setText(item.getTitle());
		content.setText(item.getContent());
	
		String text = content.getText();
		long count = text == null || text.isEmpty() ? 0 : text.chars().filter(s -> s == '\n').count();
		content.setPrefRowCount(count  < 5 ? 5 : (count > 40 ? 40 : (int)count));
		
		Platform.runLater(() -> tabItemChanging = false);
	}
	public void updateFont() {
		title.setFont(Editor.getFont());
		content.setFont(Editor.getFont());
	}
	public String getItemTitle() {
		return item.getTitle();
	}
	public Entry getItem() {
		return item;
	}
	public void updateTitle() {
		title.setTooltip(new Tooltip(Utils.treeToString(item)));
		title.setText(item.getTitle());
	}
	public void setWordWrap(boolean wrap) {
		content.setWrapText(wrap);
	}
	public String getContent() {
		return content.getText();
	}
}
