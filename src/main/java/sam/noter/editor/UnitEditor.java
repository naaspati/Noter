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
import sam.fx.helpers.FxUtils;
import sam.fxml.Button2;
import sam.myutils.MyUtilsCheck;
import sam.noter.Utils;
import sam.noter.datamaneger.EntryXML;

class UnitEditor extends BorderPane {
	protected final Label title = new Label();
	protected final TextArea content = new TextArea();
	protected EntryXML item;
	
	protected UnitEditor() {
		updateFont();
		setCenter(content);
		content.setEditable(false);

		addClass(this, "unit-editor");
		addClass(content, "content");
	}
	
	public UnitEditor(Consumer<EntryXML> onExpanded) {
		this();
		Objects.requireNonNull(onExpanded);

		Button2 expandButton = new Button2("edit", "Expand_20px.png", e -> onExpanded.accept(this.item));

		HBox titleContainer = new HBox(title, FxUtils.longPaneHbox(), expandButton);
		titleContainer.setPadding(new Insets(5, 10, 5, 10));

		addClass(titleContainer, "title-box");
		setTop(titleContainer);
	}
	
	public void setItem(EntryXML e) {
		this.item = null;
		title.setText(e.getTitle());
		content.setText(e.getContent());
	
		String text = content.getText();
		long count = MyUtilsCheck.isEmpty(text) ? 0 : text.chars().filter(s -> s == '\n').count();
		content.setPrefRowCount(count  < 5 ? 5 : (count > 40 ? 40 : (int)count));
		this.item = e;
	}
	public void updateFont() {
		title.setFont(Editor.getFont());
		content.setFont(Editor.getFont());
	}
	public String getItemTitle() {
		return item.getTitle();
	}
	public EntryXML getItem() {
		return item;
	}
	public void updateTitle() {
		if(item == null)
			return;
		
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
