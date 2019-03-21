package sam.noter.editor;

import static sam.fx.helpers.FxClassHelper.addClass;

import java.util.Objects;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import sam.fx.helpers.FxHBox;
import sam.fxml.Button2;
import sam.myutils.Checker;
import sam.noter.EntryTreeItem;
import sam.noter.Utils;

class UnitEditor extends BorderPane {
	protected final Label title = new Label();
	protected final TextArea content = new TextArea();
	protected EntryTreeItem item;
	
	protected UnitEditor() {
		setCenter(content);
		content.setEditable(false);

		addClass(this, "unit-editor");
		addClass(content, "content");
	}
	
	public UnitEditor(Consumer<EntryTreeItem> onExpanded) {
		this();
		Objects.requireNonNull(onExpanded);

		Button2 expandButton = new Button2("edit", "Expand_20px.png", e -> onExpanded.accept(this.item));

		HBox titleContainer = new HBox(title, FxHBox.maxPane(), expandButton);
		titleContainer.setPadding(new Insets(5, 10, 5, 10));

		addClass(titleContainer, "title-box");
		setTop(titleContainer);
	}
	
	public void setItem(EntryTreeItem e) {
		this.item = null;
		title.setText(e.getTitle());
		content.setText(coalesce(e.getContent()));
	
		String text = content.getText();
		long count = Checker.isEmpty(text) ? 0 : text.chars().filter(s -> s == '\n').count();
		content.setPrefRowCount(count  < 5 ? 5 : (count > 40 ? 40 : (int)count));
		this.item = e;
	}
	private String coalesce(String s) {
		return s == null ? "" : s;
	}
	public String getItemTitle() {
		return item.getTitle();
	}
	public EntryTreeItem getItem() {
		return item;
	}
	public void updateTitle() {
		if(item == null)
			return;
		
		title.setTooltip(new Tooltip(Utils.toTreeString(item, false)));
		title.setText(item.getTitle());
	}
	public void setWordWrap(boolean wrap) {
		content.setWrapText(wrap);
	}
	public String getContent() {
		return content.getText();
	}
}
