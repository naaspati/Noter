package sam.noter.editor;

import static sam.fx.helpers.FxMenu.menuitem;
import static sam.fx.helpers.FxMenu.radioMenuitem;
import static sam.noter.Utils.fx;
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

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import sam.di.ConfigKey;
import sam.di.AppConfig;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;
import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.noter.EntryTreeItem;
import sam.noter.Utils;
import sam.noter.app.Observables;
import sam.reference.WeakAndLazy;
import sam.thread.DelayedActionThread;

@Singleton
public class Editor extends BorderPane {
	@FXML private BorderPane editor;
	@FXML private Button2 backBtn;
	@FXML private Label maintitle;
	@FXML private Button2 combineContentBtn;
	@FXML private Button2 combineChildrenBtn;

	private final Consumer<EntryTreeItem> onExpanded = t -> changed(t, EXPANDED);
	private final WeakAndLazy<UnitContainer> unitsContainerWL = new WeakAndLazy<>(() -> new UnitContainer(onExpanded));
	private final WeakAndLazy<CombinedText> combinedTextWL = new WeakAndLazy<>(CombinedText::new);
	private final CenterEditor centerEditor = new CenterEditor();

	private final SimpleObjectProperty<EntryTreeItem> currentItem = new SimpleObjectProperty<>();
	private final IdentityHashMap<EntryTreeItem, Stack<ViewType>> history0 = new IdentityHashMap<>();
	private final AppConfig configManager;

	private final DelayedActionThread<Object> delay;
	private static final Object SKIP_CHANGE = new Object();
	private static final Object CHANGE = new Object();
	private volatile EntryTreeItem item;
	private volatile ViewType view;

	@Inject
	public Editor(AppConfig configManager, Observables observables) throws IOException {
		FxFxml.load(this, true);
		this.configManager = configManager;

		observables.currentItemProperty()
		.addListener((p, o, n) -> changed(n, PREVIOUS));

		delay = new DelayedActionThread<>(Optional.ofNullable(configManager.getConfig(ConfigKey.EDITOR_CHANGE_DELAY)).map(Integer::parseInt).orElse(1000), this::delayedChange);
	}

	@FXML
	private void changeEntryTreeItem(ActionEvent e) {
		ViewType t = e.getSource() == combineContentBtn ? COMBINED_TEXT : COMBINED_CHILDREN;
		changed(centerEditor.getItem(), t);
	}

	@FXML
	private void historyBack(Event e) {
		Stack<ViewType> stack = history(currentItem(), false);
		if(Checker.isNotEmpty(stack))
			stack.pop();

		changed(currentItem(), PREVIOUS);
	}

	private EntryTreeItem currentItem() {
		return currentItem.get();
	}

	public void setWordWrap(boolean wrap) {
		centerEditor.setWordWrap(wrap);
		unitsContainerWL.ifPresent(u -> u.setWordWrap(wrap));
		combinedTextWL.ifPresent(u -> u.setWrapText(wrap));
	}
	public void consume(Consumer<TextArea> e) {
		if(getCenter() != centerEditor)
			FxPopupShop.showHidePopup("no text selected", 1500);
		else
			centerEditor.consume(e);
	}

	private void changed(EntryTreeItem item, ViewType view) {
		setDisable(item == null);
		this.item = item;
		this.view = view;

		/*
		 * if(item != null && item.isContentLoaded()) {
			if(delay != null)
				delay.queue(SKIP_CHANGE);
			else
				actual_changed();
		} else {
			if(delay == null)
				delay = new DelayedActionThread<>();
		}
		 */

		delay.queue(CHANGE);

	}	
	private void delayedChange(Object obj) {
		if(obj != SKIP_CHANGE)
			fx(() -> actual_changed());
	}

	private void actual_changed() {
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
			if(Checker.isEmpty(stack))
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
	private void setCombined_text(EntryTreeItem item) {
		CombinedText c = combinedTextWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(COMBINED_TEXT, item);
		maintitle.setText(null);

		buttonsVisible();
	}
	private void setCombined_children(EntryTreeItem item) {
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

	private void addHistory(ViewType type, EntryTreeItem item) {
		if(type == null) return;
		Stack<ViewType> stack = history(item, true);
		if(stack.isEmpty() || stack.lastElement() != type)
			stack.add(type);
	}
	private Stack<ViewType> history(EntryTreeItem item, boolean create) {
		Stack<ViewType> stack = history0.get(item);

		if(create && stack == null) 
			history0.put(item, stack = new Stack<>());

		return stack;
	}

	private void setCenterEditor(EntryTreeItem item) {
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
	public void updateTitle(EntryTreeItem ti) {
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
				menuitem("copy EntryTreeItem Tree", e -> Utils.copyToClipboard(Utils.toTreeString(currentItem(), true)), currentItem.isNull()),
				radioMenuitem("Text wrap", e -> setWordWrap(((RadioMenuItem)e.getSource()).isSelected()))
				//TODO menuitem("Font", e -> setFont())
				);
		return menu;
	}
}
