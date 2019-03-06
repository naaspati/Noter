package sam.noter.bookmark;
import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.fx.helpers.FxKeyCodeUtils.combination;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.noter.bookmark.BookmarkType.CHILD;
import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.When;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView.EditEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import sam.config.Session;
import sam.di.Injector;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;
import sam.myutils.Checker;
import sam.myutils.MyUtilsException;
import sam.nopkg.EnsureSingleton;
import sam.noter.EntryTreeItem;
import sam.noter.app.AppUtils;
import sam.noter.dao.api.IRootEntry;
import sam.reference.WeakAndLazy;

@Singleton
public class BookmarksPane extends BorderPane {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{
		singleton.init();
	}

	@FXML private BookMarkTree tree;
	@FXML private Button2 addButton;           
	@FXML private Button2 addChildButton;      
	@FXML private Button2 removeButton;        
	@FXML private RadioButton expandCollpase;  
	@FXML private Button2 showHideButton;
	private final Map<Path, Integer> selectedEntry = new HashMap<>();
	
	private BooleanBinding selectedItemNull;

	private final Button2 show = new Button2("show","Chevron Right_20px.png", null) ;
	private final VBox showBox = new VBox(show);
	private final Injector injector;
	private SimpleBooleanProperty tabIsNull = new SimpleBooleanProperty();

	@Inject
	public BookmarksPane(Injector injector) throws IOException {
		FxFxml.load(this, true);
		this.injector = injector;

		showBox.setMaxWidth(20);
		showBox.setPadding(new Insets(5, 0, 0, 0));
		show.setOnAction(this::showHideAction);
		
		expandCollpase.tooltipProperty().bind(new When(expandCollpase.selectedProperty()).then(new Tooltip("collapse")).otherwise(new Tooltip("expand")));

		/* TODO
		 * 
		 * removeButton.effectProperty().bind(new When(removeButton.disableProperty()).then(GRAYSCALE_EFFECT).otherwise((ColorAdjust)null));
		removeButton.disableProperty().bind(selectedItemNull);

		addChildButton.effectProperty().bind(removeButton.effectProperty());
		addChildButton.disableProperty().bind(removeButton.disableProperty());

		addButton.effectProperty().bind(new When(addButton.disableProperty()).then(GRAYSCALE_EFFECT).otherwise((ColorAdjust)null));
		 */
		
	}
	
	public EntryTreeItem getRoot() {
		return (EntryTreeItem) tree.getRoot();
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
		d.initOwner(Session.global().get(Stage.class));
		d.showAndWait()
		.ifPresent(s -> {
			if(Checker.isEmptyTrimmed(s)) {
				FxPopupShop.showHidePopup("bad title:", 1500);
				return;
			}
			if(s.equals(e.getOldValue()))
				return;

			EntryTreeItem ti = (EntryTreeItem) e.getTreeItem();
			ti.setTitle(s);
			//FIXME editor.updateTitle(ti);
		});
	}

	@FXML
	public void expandCollpaseAction(ActionEvent e) {
		TreeItem<String> ti = tree.getSelectedItem();
		expandBookmarks(tree.getRoot().getChildren(), expandCollpase.isSelected());
		if(ti != null)
			tree.model().select(ti);
	}

	@FXML
	public void addAction(ActionEvent e) {
		if(e.getSource() == addChildButton)
			addNewBookmark(CHILD);
		else if(e.getSource() == addButton)
			addNewBookmark(RELATIVE);
	}
	
	private final WeakAndLazy<BookmarkAddeder> adder = new WeakAndLazy<>(() -> adderCreate());
	
	private BookmarkAddeder adderCreate() {
		return MyUtilsException.noError(() -> new BookmarkAddeder(injector.instance(AppUtils.class)));
	}

	private void addNewBookmark(BookmarkType bookMarkType) {
		adder.get().showDialog(bookMarkType, tree);
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
				menuitem("Add Bookmark", combination(KeyCode.N, SHORTCUT_DOWN), e -> addNewBookmark(RELATIVE), tabIsNull),
				menuitem("Add Child Bookmark", combination(KeyCode.N, SHORTCUT_DOWN, SHIFT_DOWN), e -> addNewBookmark(CHILD), selectedItemNull),
				menuitem("Add Bookmark Relative to Parent", combination(KeyCode.N, ALT_DOWN, SHIFT_DOWN), e -> addNewBookmark(RELATIVE_TO_PARENT), selectedItemNull),
				new SeparatorMenuItem(),
				menuitem("Remove bookmark", this::removeAction, selectedItemNull),
				//FIXME menuitem("Undo Removed bookmark", e -> remover().undoRemoveBookmark(tab), undoDeleteSize.isEqualTo(0)),
				new SeparatorMenuItem()
				//FIXME , menuitem("Move bookmark", e -> mover().moveBookmarks(tree.model()), selectedItemNull)
				);
	}
	@FXML
	private void removeAction(ActionEvent e) {
		//FIXME remover().removeAction(tree.model(), tab);
	}
	/** FIXME
	 * 	private BookmarkRemover remover; 
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
			mover = injector.instance(BookmarkMover.class);
		return mover;
	}
	 * @param root
	 */

	public void set(IRootEntry root) {
		tabIsNull.set(root == null);
		
		/* FIXME
		 * if(remover != null) {
			remover.tabClosed(this.rootEntry);
			remover.switchTab(root);
		}
		 */
		
		IRootEntry old = tree.getRootEntry();
		if(old != null)
			selectedEntry.put(old.getJbookPath(), Optional.ofNullable(tree.getSelectedItem()).map(e -> e.getId()).orElse(null));
		
		this.tree.setRootEntry(root);
		
		if(root != null) {
			Integer id = selectedEntry.remove(root.getJbookPath());
			if(id != null)
				this.tree.selectById(id);
		}
	}
}
