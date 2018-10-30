package sam.noter.bookmark;


import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.fx.helpers.FxKeyCodeCombination.combination;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.noter.App.GRAYSCALE_EFFECT;
import static sam.noter.bookmark.BookmarkType.CHILD;
import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.When;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeView.EditEvent;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import sam.config.Session;
import sam.fx.helpers.FxFxml;
import sam.fxml.Button2;
import sam.noter.dao.Entry;
import sam.noter.editor.Editor;
import sam.noter.tabs.Tab;
import sam.noter.tabs.TabContainer;
import sam.reference.ReferenceUtils;
import sam.reference.WeakAndLazy;
public class BookmarksPane extends BorderPane implements ChangeListener<Tab> {

	@FXML
	private TreeView<String> tree;
	private final MultipleSelectionModel<TreeItem<String>> selectionModel;
	private final BooleanBinding selectedItemNull;

	@FXML private Button2 addButton;           
	@FXML private Button2 addChildButton;      
	@FXML private Button2 removeButton;        
	@FXML private RadioButton expandCollpase;  
	@FXML private Button2 showHideButton;      

	private final Button2 show = new Button2("show","Chevron Right_20px.png", null) ;
	private final VBox showBox = new VBox(show);
	private final ReadOnlyObjectProperty<Tab> currentTab;
	private final Editor editor;
	private final TabContainer tabcontainer;
	private final HashMap<Tab, WeakReference<TreeItem<String>>> history = new HashMap<>();

	public BookmarksPane(Editor editor, TabContainer tabcontainer) throws IOException {
		FxFxml.load(this, true);
		this.currentTab = tabcontainer.currentTabProperty();
		this.editor = editor;
		this.tabcontainer = tabcontainer;

		this.selectionModel = tree.getSelectionModel();
		selectionModel.selectedItemProperty()
		.addListener((p, o, n) -> {
			if(n != null)
				n.getChildren();
		});
		this.selectionModel.setSelectionMode(SelectionMode.MULTIPLE);
		this.selectedItemNull = selectionModel.selectedItemProperty().isNull();

		this.adder = new WeakAndLazy<>(BookmarkAddeder::new);

		tree.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
			if(e.getClickCount() > 1) {
				e.consume();
				return;
			}
		});

		showBox.setMaxWidth(20);
		showBox.setPadding(new Insets(5, 0, 0, 0));
		show.setOnAction(this::showHideAction);

		expandCollpase.tooltipProperty().bind(new When(expandCollpase.selectedProperty()).then(new Tooltip("collapse")).otherwise(new Tooltip("expand")));

		removeButton.effectProperty().bind(new When(removeButton.disableProperty()).then(GRAYSCALE_EFFECT).otherwise((ColorAdjust)null));
		removeButton.disableProperty().bind(selectedItemNull);

		addChildButton.effectProperty().bind(removeButton.effectProperty());
		addChildButton.disableProperty().bind(removeButton.disableProperty());

		addButton.effectProperty().bind(new When(addButton.disableProperty()).then(GRAYSCALE_EFFECT).otherwise((ColorAdjust)null));

		BooleanBinding currentTabNull = currentTab.isNull();

		addButton.disableProperty().bind(currentTabNull);
		expandCollpase.disableProperty().bind(currentTabNull);

		currentTab.addListener(this);
		this.disableProperty().bind(currentTabNull);
	}

	@FXML
	private void showHideAction(ActionEvent e) {
		boolean isShow = e.getSource() == show;
		Node node = isShow ? showBox : this;
		SplitPane container = (SplitPane)(node.getParent().getParent());
		List<Node> list = container.getItems();
		list.set(list.indexOf(node), isShow ? this : showBox);
		container.setDividerPositions(0.3);
	}
	@FXML
	private void bookmarksEditStart(EditEvent<String> e) {
		TextInputDialog d = new TextInputDialog(e.getOldValue());
		d.setHeaderText("Rename Bookmark");
		d.setTitle("Rename");
		d.initModality(Modality.APPLICATION_MODAL);
		d.initOwner(Session.get(Stage.class));
		d.showAndWait()
		.ifPresent(s -> {
			if(s == null || s.equals(e.getOldValue()))
				return;

			Entry ti = (Entry) e.getTreeItem();
			ti.setTitle(s);
			editor.updateTitle(ti);
		});
	}

	public MultipleSelectionModel<TreeItem<String>> getSelectionModel() {
		return selectionModel;
	}

	@FXML
	public void expandCollpaseAction(ActionEvent e) {
		TreeItem<String> ti = selectionModel.getSelectedItem();
		expandBookmarks(tree.getRoot().getChildren(), expandCollpase.isSelected());
		if(ti != null)
			selectionModel.select(ti);
	}
	private Tab currentTab() {
		return currentTab.get();
	}

	@FXML
	public void addAction(ActionEvent e) {
		if(e.getSource() == addChildButton)
			addNewBookmark(CHILD);
		else if(e.getSource() == addButton)
			addNewBookmark(RELATIVE);
	}

	private final WeakAndLazy<BookmarkAddeder> adder;

	private void addNewBookmark(BookmarkType bookMarkType) {
		adder.get().addNewBookmark(bookMarkType, selectionModel, tree, currentTab());
	}
	private void expandBookmarks(List<TreeItem<String>> children, boolean expanded) {
		if(children.isEmpty())
			return;

		for (TreeItem<String> t : children) {
			t.setExpanded(expanded);
			expandBookmarks(t.getChildren(), expanded);
		}
	}

	public Menu getBookmarkMenu() {
		return new Menu("_Bookmark",
				null,
				menuitem("Add Bookmark", combination(KeyCode.N, SHORTCUT_DOWN), e -> addNewBookmark(RELATIVE), currentTab.isNull()),
				menuitem("Add Child Bookmark", combination(KeyCode.N, SHORTCUT_DOWN, SHIFT_DOWN), e -> addNewBookmark(CHILD), selectedItemNull),
				menuitem("Add Bookmark Relative to Parent", combination(KeyCode.N, ALT_DOWN, SHIFT_DOWN), e -> addNewBookmark(RELATIVE_TO_PARENT), selectedItemNull),
				new SeparatorMenuItem(),
				menuitem("Remove bookmark", this::removeAction, selectedItemNull),
				menuitem("Undo Removed bookmark", e -> remover().undoRemoveBookmark(currentTab()), undoDeleteSize.isEqualTo(0)),
				new SeparatorMenuItem(),
				menuitem("Move bookmark", e -> mover().moveBookmarks(currentTab(), selectionModel), selectedItemNull)
				);
	}
	@FXML
	private void removeAction(ActionEvent e) {
		remover().removeAction(selectionModel, currentTab());
	}

	private BookmarkRemover remover; 
	private final SimpleIntegerProperty undoDeleteSize = new SimpleIntegerProperty();

	private BookmarkRemover remover() {
		if(remover == null) {
			remover = new BookmarkRemover();
			undoDeleteSize.bind(remover.undoDeleteSize);
		}
		return remover;
	}
	private BookmarkMover mover;
	private BookmarkMover mover() {
		if(mover == null)
			mover = new BookmarkMover(tabcontainer);
		return mover;
	}

	@Override
	public void changed(ObservableValue<? extends Tab> observable, Tab oldValue, Tab newValue) {
		if(remover != null) {
			remover.tabClosed(oldValue);
			remover.switchTab(newValue);
		}

		if(oldValue != null && tree.getRoot() != null)
			history.put(oldValue, new  WeakReference<>(selectionModel.getSelectedItem()));

		selectionModel.clearSelection();
		Entry root = newValue == null ?  null : newValue.getRoot(); 
		tree.setRoot(root);

		if(root != null){
			TreeItem<String> item = ReferenceUtils.get(history.get(newValue));
			if(item != null) selectionModel.select(item);
			else {
				if(!root.getChildren().isEmpty())
					selectionModel.select(root.getChildren().get(0));
			}
		}
	}
	public ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty() {
		return selectionModel.selectedItemProperty();
	}

	void clearAndSelect(Entry entry) {
		selectionModel.clearSelection();
		selectionModel.select(entry);
	}
}
