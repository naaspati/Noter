package sam.noter.editor;

import static sam.noter.editor.ViewType.CENTER;
import static sam.noter.editor.ViewType.COMBINED_CHILDREN;
import static sam.noter.editor.ViewType.COMBINED_TEXT;
import static sam.noter.editor.ViewType.EXPANDED;
import static sam.noter.editor.ViewType.PREVIOUS;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

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
import sam.config.Session;
import sam.config.SessionPutGet;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsCheck;
import sam.noter.datamaneger.EntryXML;
import sam.noter.tabs.Tab;
import sam.noter.tabs.TabContainer;
import sam.reference.WeakAndLazy;

public class Editor extends BorderPane implements SessionPutGet {

	@FXML private BorderPane editor;
	@FXML private Button2 backBtn;
	@FXML private Label maintitle;
	@FXML private Button2 combineContentBtn;
	@FXML private Button2 combineChildrenBtn;

	private final Consumer<EntryXML> onExpanded = t -> changed(t, EXPANDED);
	private WeakAndLazy<UnitContainer> unitsContainerWL = new WeakAndLazy<>(() -> new UnitContainer(onExpanded));
	private WeakAndLazy<CombinedText> combinedTextWL = new WeakAndLazy<>(CombinedText::new);
	private final CenterEditor centerEditor = new CenterEditor();

	private EntryXML currentItem;
	private final IdentityHashMap<EntryXML, Stack<ViewType>> history0 = new IdentityHashMap<>();

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

	public Editor() throws IOException {
		FxFxml.load(this, true);
	}
	
	private Tab tab;
	
	public void init(ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty, TabContainer container){
		Objects.requireNonNull(selectedItemProperty);
		selectedItemProperty.addListener((p, o, n) -> changed((EntryXML)n, PREVIOUS));
		disableProperty().bind(selectedItemProperty.isNull());
		container.currentTabProperty().addListener((p, o, n) -> {tab = n;});
		container.addOnTabClosing(tab -> {
			if(tab == this.tab)
				changed(null, CENTER);
		});
	}
	@FXML
	private void changeEntry(ActionEvent e) {
		ViewType t = e.getSource() == combineContentBtn ? COMBINED_TEXT : COMBINED_CHILDREN;
		changed(centerEditor.getItem(), t);
	}

	@FXML
	private void historyBack(Event e) {
		Stack<ViewType> stack = history(currentItem, false);
		if(MyUtilsCheck.isNotEmpty(stack))
			stack.pop();
		
		changed(currentItem, PREVIOUS);
	}

	public void setWordWrap(boolean wrap) {
		centerEditor.setWordWrap(wrap);
		unitsContainerWL.ifPresent(u -> u.setWordWrap(wrap));
		combinedTextWL.ifPresent(u -> u.setWrapText(wrap));
	}
	public void setFont() {
		Font font = new FontSetter().getFont();
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
	private void changed(EntryXML item, ViewType view) {
		if(item == null) {
			unitsContainerWL.ifPresent(UnitContainer::clear);
			combinedTextWL.ifPresent(CombinedText::clear);
			centerEditor.setItem(null);
			
			maintitle.setText(null);
			super.setCenter(null);
			backBtn.setVisible(false);
			combineChildrenBtn.setVisible(false);
			combineContentBtn.setVisible(false);
			
			currentItem = null;
			return;
		}

		Objects.requireNonNull(view);
		
		if(view == PREVIOUS) {
			Stack<ViewType> stack = history(item,false);
			if(MyUtilsCheck.isEmpty(stack))
				view = CENTER;
			else 
				view = stack.pop();
		};

		switch (view) {
			case EXPANDED:
				setCenterEditor(item);
				addHistory(EXPANDED,  item);
				buttonsVisible();
				break;
			case CENTER:
				setCenterEditor(item);
				history0.remove(item);
				backBtn.setVisible(false);
				combineChildrenBtn.setVisible(!item.isEmpty());
				combineContentBtn.setVisible(!item.isEmpty());
				break;
			case COMBINED_CHILDREN:
				setCombined_children(item);
				break;
			case COMBINED_TEXT:
				setCombined_text(item);
				break;
			default:
				throw new IllegalArgumentException("unknown view: "+view);
		}
		currentItem = item;
	}
	private void setCombined_text(EntryXML item) {
		CombinedText c = combinedTextWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(COMBINED_TEXT, item);
		maintitle.setText(null);

		buttonsVisible();
	}
	private void setCombined_children(EntryXML item) {
		UnitContainer c = unitsContainerWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(COMBINED_CHILDREN,item);
		maintitle.setText(item.getTitle());

		buttonsVisible();
	}

	private void buttonsVisible() {
		backBtn.setVisible(true);
		combineChildrenBtn.setVisible(false);
		combineContentBtn.setVisible(false);
	}

	private void addHistory(ViewType type, EntryXML item) {
		if(type == null) return;
		Stack<ViewType> stack = history(item, true);
		if(stack.isEmpty() || stack.lastElement() != type)
			stack.add(type);
	}
	private Stack<ViewType> history(EntryXML item, boolean create) {
		Stack<ViewType> stack = history0.get(item);

		if(create && stack == null) 
			history0.put(item, stack = new Stack<>());

		return stack;
	}

	private void setCenterEditor(EntryXML item) {
		if(getCenter() != centerEditor)
			setCenter(centerEditor);

		centerEditor.setItem(item);
		maintitle.setText(item.getTitle());

		unitsContainerWL.ifPresent(UnitContainer::clear);
		combinedTextWL.ifPresent(CombinedText::clear);

		combineChildrenBtn.setVisible(!item.getChildren().isEmpty());
		combineContentBtn.setVisible(!item.getChildren().isEmpty());
	}
	public void updateTitle(EntryXML ti) {
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
}
