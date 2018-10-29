package sam.noter.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import sam.logging.InitFinalized;
import sam.noter.datamaneger.Entry;
import sam.noter.tabs.Tab;
import sam.noter.tabs.TabContainer;

class BookmarkMover extends Stage implements InitFinalized, EventHandler<ActionEvent> {
	private final Button moveAbove = new Button("Move Above");
	private final Button moveBelow = new Button("Move Below");
	private final Button moveAsFirstChild = new Button("Move As First Child");
	private final Button moveAsLastChild = new Button("Move As Last Child");
	private final TabContainer tabcontainer;

	private TreeItem<TreeItem<String>> root; 
	private final TreeView<TreeItem<String>> view = new TreeView<>();
	private MultipleSelectionModel<TreeItem<String>> selectionModel;
	private Tab  currentTab, selectedTab;
	private FlowPane tabs = new FlowPane();

	public BookmarkMover(TabContainer tabcontainer, Window stage) {
		super(StageStyle.UNIFIED);
		initModality(Modality.APPLICATION_MODAL);
		initOwner(stage);
		this.tabcontainer = tabcontainer;

		moveAbove.setOnAction(this);
		moveBelow.setOnAction(this);
		moveAsFirstChild.setOnAction(this);
		moveAsLastChild.setOnAction(this);

		view.setShowRoot(false);
		view.setCellFactory(tt -> new TreeCell<TreeItem<String>>() {
			@Override
			protected void updateItem(TreeItem<String> item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty ? null : item.getValue());
			}
		});

		VBox buttons = new VBox(10, moveAbove,moveBelow,moveAsFirstChild,moveAsLastChild);
		buttons.setPadding(new Insets(10));
		buttons.setAlignment(Pos.CENTER);
		buttons.disableProperty().bind(view.getSelectionModel().selectedItemProperty().isNull());

		view.setId("bookmarks");

		Scene scene = new Scene(new BorderPane(new HBox(view,buttons), tabs, null, null, null));
		scene.getStylesheets().add("css/style.css");
		setScene(scene);
		setResizable(false);

		init();
	}

	public void moveBookmarks(Tab  selectedTab, MultipleSelectionModel<TreeItem<String>> selectionModel) {
		this.currentTab = selectedTab;
		this.selectionModel = selectionModel;
		tabs.getChildren().clear();
		tabcontainer.forEach(t -> tabs.getChildren().add(tab(t)));
		tabs.setVisible(tabs.getChildren().size() != 1);
		((Hyperlink)tabs.getChildren().get(0)).fire();
		showAndWait();
	}

	private Node tab(Tab t) {
		Hyperlink h = new Hyperlink(t.getTabTitle());
		h.setOnAction(this);
		h.setUserData(t);
		return h;
	}

	private TreeItem<TreeItem<String>> fillRootItem(TreeItem<String> root, List<TreeItem<String>> selectedItems) {
		TreeItem<TreeItem<String>> item = new TreeItem<>(root);
		for (TreeItem<String> ti : root.getChildren()) {
			if(!selectedItems.contains(ti))
				item.getChildren().add(fillRootItem(ti, selectedItems));
		}
		return item;
	}

	@Override
	public void handle(ActionEvent e1) {
		Node node = (Node) e1.getSource();
		if(node.getUserData()  != null) {
			selectedTab = (Tab) node.getUserData();
			root = fillRootItem(selectedTab.getRootItem(), selectedTab == currentTab ? selectionModel.getSelectedItems() : Collections.emptyList());
			view.setRoot(root);
			return;
		}

		Entry item = (Entry)view.getSelectionModel().getSelectedItem().getValue();
		Entry parent = (Entry) item.getParent(); 
		int index = parent.indexOf(item);
		List<TreeItem<String>> list = new ArrayList<>(selectionModel.getSelectedItems());
		selectionModel.clearSelection();
		boolean b = selectedTab != currentTab;
		list.forEach(t -> {
			Entry e = (Entry)t; 
			((Entry)e.getParent()).remove(t);
			if(b) 
				e.removeElement();
		});

		if(node == moveAbove)
			parent.addAll(index-1, list);
		else if(node == moveBelow)
			parent.addAll(index+1, list);
		else if(node == moveAsFirstChild) 
			item.addAll(0, list);
		else if(node == moveAsLastChild) 
			item.addAll(list);

		hide();
		selectionModel.clearSelection();
		selectionModel.select(list.get(0));
	}

	@Override
	protected void finalize() throws Throwable {
		finalized(); 
		super.finalize();
	}
}
