package sam.noter.editor;

import static sam.fx.helpers.FxMenu.menuitem;
import static sam.fx.helpers.FxMenu.radioMenuitem;
import static sam.noter.editor.ViewType.CENTER;
import static sam.noter.editor.ViewType.COMBINED_CHILDREN;
import static sam.noter.editor.ViewType.COMBINED_TEXT;
import static sam.noter.editor.ViewType.EXPANDED;
import static sam.noter.editor.ViewType.PREVIOUS;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import sam.config.Session;
import sam.config.SessionHelper;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsCheck;
import sam.noter.Utils;
import sam.noter.dao.Entry;
import sam.noter.tabs.Tab;
import sam.noter.tabs.TabContainer;
import sam.reference.WeakAndLazy;
import sam.thread.DelayedQueueThread;

public class Editor extends BorderPane implements SessionHelper {

	@FXML private BorderPane editor;
	@FXML private Button2 backBtn;
	@FXML private Label maintitle;
	@FXML private Button2 combineContentBtn;
	@FXML private Button2 combineChildrenBtn;

	private final Consumer<Entry> onExpanded = t -> changed(t, EXPANDED);
	private final WeakAndLazy<UnitContainer> unitsContainerWL = new WeakAndLazy<>(() -> new UnitContainer(onExpanded));
	private final WeakAndLazy<CombinedText> combinedTextWL = new WeakAndLazy<>(CombinedText::new);
	private final CenterEditor centerEditor = new CenterEditor();

	private final SimpleObjectProperty<Entry> currentItem = new SimpleObjectProperty<>();
	private final IdentityHashMap<Entry, Stack<ViewType>> history0 = new IdentityHashMap<>();

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
			MyLoggerFactory.logger(Editor.class);
		}
		return null;
	}

	public Editor() throws IOException {
		FxFxml.load(this, true);
	}
	
	private Tab tab;
	
	public void init(ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty, TabContainer container){
		Objects.requireNonNull(selectedItemProperty);
		selectedItemProperty.addListener((p, o, n) -> changed((Entry)n, PREVIOUS));
		disableProperty().bind(selectedItemProperty.isNull());
		container.currentTabProperty().addListener((p, o, n) -> {tab = n;});
		container.addOnTabClosing(tab -> {
			if(tab == this.tab)
				centerEditor.commit();
		});
	}
	@FXML
	private void changeEntry(ActionEvent e) {
		ViewType t = e.getSource() == combineContentBtn ? COMBINED_TEXT : COMBINED_CHILDREN;
		changed(centerEditor.getItem(), t);
	}

	@FXML
	private void historyBack(Event e) {
		Stack<ViewType> stack = history(currentItem(), false);
		if(MyUtilsCheck.isNotEmpty(stack))
			stack.pop();
		
		changed(currentItem(), PREVIOUS);
	}

	private Entry currentItem() {
		return currentItem.get();
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
		else
			centerEditor.consume(e);
	}
	private DelayedQueueThread<Object> delay;
	private static final Object SKIP_CHANGE = new Object();
	
	private void changed(Entry item, ViewType view) {
		if(item != null && item.isContentLoaded()) {
			if(delay != null)
				delay.add(SKIP_CHANGE);
			actual_changed(item, view);
			return;
		}
		if(delay == null) {
			delay = new DelayedQueueThread<>(Optional.ofNullable(sessionGetProperty("change.delay")).map(Integer::parseInt).orElse(1000), this::delayedChange);
			delay.start();
		}
		delay.add(new Object[]{item, view});
	}	
	private void delayedChange(Object obj) {
		if(obj == SKIP_CHANGE)
			return;
		Object[] oo = (Object[])obj;
		Platform.runLater(() -> actual_changed((Entry)oo[0], (ViewType)oo[1]));
	}

	private void actual_changed(Entry item, ViewType view) {
		if(item == null) {
			unitsContainerWL.ifPresent(UnitContainer::clear);
			combinedTextWL.ifPresent(CombinedText::clear);
			centerEditor.setItem(null);
			
			maintitle.setText(null);
			super.setCenter(null);
			backBtn.setVisible(false);
			combineChildrenBtn.setVisible(false);
			combineContentBtn.setVisible(false);
			
			currentItem.set(null);
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
		currentItem.set(item);
	}
	private void setCombined_text(Entry item) {
		CombinedText c = combinedTextWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(COMBINED_TEXT, item);
		maintitle.setText(null);

		buttonsVisible();
	}
	private void setCombined_children(Entry item) {
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

	private void addHistory(ViewType type, Entry item) {
		if(type == null) return;
		Stack<ViewType> stack = history(item, true);
		if(stack.isEmpty() || stack.lastElement() != type)
			stack.add(type);
	}
	private Stack<ViewType> history(Entry item, boolean create) {
		Stack<ViewType> stack = history0.get(item);

		if(create && stack == null) 
			history0.put(item, stack = new Stack<>());

		return stack;
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
	@Deprecated //listen Tab changelistenr for title changes
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

	public Menu getEditorMenu() {
		Menu menu = new Menu("editor", null,
				menuitem("copy Entry Tree", e -> Utils.copyToClipboard(currentItem().toTreeString(true)), currentItem.isNull()),
				radioMenuitem("Text wrap", e -> setWordWrap(((RadioMenuItem)e.getSource()).isSelected())),
				menuitem("Font", e -> setFont())
				);
		return menu;
	}
}
