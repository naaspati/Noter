package sam.apps.jbook_reader;


import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.apps.jbook_reader.App.GRAYSCALE_EFFECT;
import static sam.fx.helpers.FxKeyCodeCombination.combination;
import static sam.fx.helpers.FxMenu.menuitem;

import java.io.IOException;
import java.util.List;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.When;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import sam.apps.jbook_reader.datamaneger.Entry;
import sam.apps.jbook_reader.tabs.Tab;
import sam.fx.helpers.FxFxml;
import sam.fxml.Button2;

public class BookmarksPane extends BorderPane {

	@FXML
	private TreeView<String> tree;
	private final MultipleSelectionModel<TreeItem<String>> selectionModel;
	private final BooleanBinding selectedItemNull;
	
	@FXML private Button2 addButton;           
	@FXML private Button2 addChildButton;      
	@FXML private Button2 removeButton;        
	@FXML private RadioButton expandCollpase;  
	@FXML private Button2 showHideButton;      
	
	private final Actions actions = Actions.getInstance();

	private final Button show = new Button2("show","Chevron Right_20px.png", null) ;
	private final VBox showBox = new VBox(show);
	private final ReadOnlyObjectWrapper<Tab> currentTab;

	public BookmarksPane(ReadOnlyObjectWrapper<Tab> currentTab) throws IOException {
		FxFxml.load(this, true);
		this.currentTab = currentTab;

		this.selectionModel = tree.getSelectionModel();
		this.selectionModel.setSelectionMode(SelectionMode.MULTIPLE);
		this.selectedItemNull = selectionModel.selectedItemProperty().isNull();

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
		d.initOwner(App.getStage());
		d.showAndWait()
		.ifPresent(s -> {
			if(s == null || s.equals(e.getOldValue()))
				return;

			Entry ti = (Entry) e.getTreeItem();
			ti.setTitle(s);
			App.getInstance().editor().updateTitle(ti);
		});
	}

	public MultipleSelectionModel<TreeItem<String>> getSelectionModel() {
		return selectionModel;
	}
	
	@FXML
	public void removeAction(ActionEvent e) {
		 actions.removeBookmarkAction(tree, getCurrentTab());
	}
	
	@FXML
	public void expandCollpaseAction(ActionEvent e) {
		TreeItem<String> ti = selectionModel.getSelectedItem();
		expandBookmarks(tree.getRoot().getChildren(), expandCollpase.isSelected());
		if(ti != null)
			selectionModel.select(ti);
	}
	private Tab getCurrentTab() {
		return currentTab.get();
	}

	@FXML
	public void addAction(ActionEvent e) {
		//TODO
		if(e.getSource() == addChildButton)
			actions.addNewBookmark(tree, getCurrentTab(), BookmarkType.CHILD);
		else if(e.getSource() == addButton)
				actions.addNewBookmark(tree, getCurrentTab(), BookmarkType.RELATIVE);
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
				menuitem("Add Bookmark", combination(KeyCode.N, SHORTCUT_DOWN), e -> actions.addNewBookmark(tree, getCurrentTab(), BookmarkType.RELATIVE), currentTab.isNull()),
				menuitem("Add Child Bookmark", combination(KeyCode.N, SHORTCUT_DOWN, SHIFT_DOWN), e -> actions.addNewBookmark(tree, getCurrentTab(), BookmarkType.CHILD), selectedItemNull),
				menuitem("Add Bookmark Relative to Parent", combination(KeyCode.N, ALT_DOWN, SHIFT_DOWN), e -> actions.addNewBookmark(tree, getCurrentTab(), BookmarkType.RELATIVE_TO_PARENT), selectedItemNull),
				new SeparatorMenuItem(),
				menuitem("Remove bookmark", e -> actions.removeBookmarkAction(tree, getCurrentTab()), selectedItemNull),
				menuitem("Undo Removed bookmark", e -> actions.undoRemoveBookmark(getCurrentTab()), actions.undoDeleteSizeProperty().isEqualTo(0)),
				new SeparatorMenuItem(),
				menuitem("Move bookmark", e -> actions.moveBookmarks(tree, selectionModel.getSelectedItems()), selectedItemNull)
				);
	}
	public void setRoot(TreeItem<String> root) {
		tree.setRoot(root);
	}
}
