package sam.noter.editor;

import static sam.noter.editor.ViewType.CENTER;
import static sam.noter.editor.ViewType.COMBINED_CHILDREN;
import static sam.noter.editor.ViewType.COMBINED_TEXT;
import static sam.noter.editor.ViewType.EXPANDED;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import sam.config.Session;
import sam.config.SessionPutGet;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsCheck;
import sam.noter.datamaneger.Entry;
import sam.reference.WeakAndLazy;

public class Editor extends BorderPane implements SessionPutGet {
	private static final Logger LOGGER = MyLoggerFactory.logger(Editor.class.getSimpleName());

	@FXML private BorderPane editor;
	@FXML private Button2 backBtn;
	@FXML private Label maintitle;
	@FXML private Button2 combineContentBtn;
	@FXML private Button2 combineChildrenBtn;

	private final Consumer<Entry> onExpanded = t -> changed(t, EXPANDED);
	private WeakAndLazy<UnitContainer> unitsContainerWL = new WeakAndLazy<>(() -> new UnitContainer(onExpanded));
	private WeakAndLazy<CombinedText> combinedTextWL = new WeakAndLazy<>(CombinedText::new);
	private final CenterEditor centerEditor = new CenterEditor();
	
	private Entry currentItem;
	private ViewType currentView;

	private final Window parent;
	private final IdentityHashMap<Entry, Stack<ViewType>> history = new IdentityHashMap<>();

	private static Font font;

	public static Font getFont() {
		return font;
	}
	static {
		// Font.font(family, weight, posture, size)
		String family = Session.getProperty(Editor.class, "font.family");
		FontWeight weight = parse("font.weight", FontWeight::valueOf);
		FontPosture posture = parse("font.posture", FontPosture::valueOf);
		Float size = parse("font.size", Float::parseFloat);

		font = Font.font(family, weight, posture, size == null ? -1 : size);
	}
	private static <R> R parse(String key, Function<String, R> parser) {
		try {
			String s = Session.getProperty(Editor.class, key);
			if(s == null)
				return null;
			return parser.apply(s.toUpperCase());
		} catch (Exception e) {
			MyLoggerFactory.logger(Editor.class.getName()).warning("bad "+key+" value: "+Session.getProperty(Editor.class, key));
		}
		return null;
	}

	public Editor(Window parent) throws IOException {
		FxFxml.load(this, true);
		this.parent = parent;
	}
	public void init(ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty){
		Objects.requireNonNull(selectedItemProperty);
		selectedItemProperty.addListener((p, o, n) -> changed((Entry)n, null));
		disableProperty().bind(selectedItemProperty.isNull());		
	}
	@FXML
	private void changeEntry(ActionEvent e) {
		ViewType t = e.getSource() == combineContentBtn ? COMBINED_TEXT : COMBINED_CHILDREN;
		changed(centerEditor.getItem(), t);
	}

	@FXML
	private void historyBack(Event e) {
		Stack<ViewType> stack = history.get(currentItem);
		if(stack == null || stack.isEmpty()){
			backBtn.setDisable(true);
			return;
		}
		changed(currentItem, stack.pop());
	}

	public void setWordWrap(boolean wrap) {
		centerEditor.setWordWrap(wrap);
		unitsContainerWL.ifPresent(u -> u.setWordWrap(wrap));
		combinedTextWL.ifPresent(u -> u.setWrapText(wrap));
	}
	public void setFont() {
		Font font = new FontSetter(parent).getFont();
		if(font == null) return;
		
		centerEditor.updateFont();
		unitsContainerWL.ifPresent(UnitContainer::updateFont);
		combinedTextWL.ifPresent(t -> t.setFont(font));

	}

	public void consume(Consumer<TextArea> e) {
		if(getCenter() != centerEditor)
			FxPopupShop.showHidePopup("no text selected", 1500);
		else {

			centerEditor.consume(e);
		}
	}
	public void changed(Entry item, ViewType view) {
		if(item == null) {
			unitsContainerWL.ifPresent(UnitContainer::clear);
			maintitle.setText(null);
			super.setCenter(null);
			backBtn.setVisible(false);
			combineChildrenBtn.setVisible(false);
			combineContentBtn.setVisible(false);
			return;
		}
		
		if(view == null) {
			Stack<ViewType> stack = history.get(item);
			if(MyUtilsCheck.isEmpty(stack))
				view = CENTER;
			else 
				view = stack.pop();
		};

		switch (view) {
			case EXPANDED:
				setCenterEditor(item);
				addHistory(item);
				break;
			case CENTER:
				setCenterEditor(item);
				history.remove(item);
				backBtn.setVisible(false);
				break;
			case COMBINED_CHILDREN:
				setCombined_children(item);
				break;
			case COMBINED_TEXT:
				setCombined_text(item);
				break;
		}
		
		currentItem = item;
		currentView = view;
	}
	private void setCombined_text(Entry item) {
		CombinedText c = combinedTextWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(item);
		maintitle.setText(null);

		combineChildrenBtn.setVisible(true);
		combineContentBtn.setVisible(false);
	}
	private void setCombined_children(Entry item) {
		UnitContainer c = unitsContainerWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(item);
		maintitle.setText(item.getTitle());

		combineChildrenBtn.setVisible(false);
		combineContentBtn.setVisible(true);
	}

	private void addHistory(Entry item) {
		ViewType type = currentView;
		if(type == null) return;
		Stack<ViewType> stack = history.computeIfAbsent(item, v -> new Stack<>());
		if(stack.isEmpty() || stack.lastElement() != type){
			stack.add(type);
			LOGGER.fine(() -> "ADD TO HISTORY: "+type+":  "+item.getTitle());
		}
		backBtn.setVisible(!stack.isEmpty());
	}
	private void setCenterEditor(Entry item) {
		if(getCenter() != centerEditor)
			setCenter(centerEditor);

		centerEditor.setItem(item);
		maintitle.setText(item.getTitle());

		unitsContainerWL.ifPresent(UnitContainer::clear);
		combinedTextWL.ifPresent(CombinedText::clear);
		
		combineChildrenBtn.setVisible(!item.getChildren().isEmpty());
		combineContentBtn.setVisible(!item.getChildren().isEmpty());
	}
	public void updateTitle(Entry ti) {
		unitsContainerWL.ifPresent(u -> u.updateTitle(ti));

		centerEditor.updateTitle();
		if(getCenter() == centerEditor)
			maintitle.setText(centerEditor.getItemTitle());
		else {
			UnitContainer u = unitsContainerWL.peek();

			if(u == null || u.isEmpty())
				maintitle.setText(null);
			else
				maintitle.setText(u.first().getItemTitle());
		}
	}
	public void close() {
		centerEditor.close();
	}

}
