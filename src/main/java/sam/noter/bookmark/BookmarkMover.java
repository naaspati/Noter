package sam.noter.bookmark;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
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
import sam.config.SessionFactory;
import sam.noter.InitFinalized;
import sam.noter.dao.Entry;
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
	private Tab  currentTab;
	private FlowPane tabs = new FlowPane();

	public BookmarkMover(TabContainer tabcontainer) {
		super(StageStyle.UNIFIED);
		initModality(Modality.APPLICATION_MODAL);
		initOwner(SessionFactory.sharedSession().get(Stage.class));
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

	public void moveBookmarks(MultipleSelectionModel<TreeItem<String>> selectionModel) {
		this.currentTab = tabcontainer.getCurrentTab();
		this.selectionModel = selectionModel;
		
		List<Node> list = tabs.getChildren(); 
		list.clear();
		list.add(tab(currentTab));
		
		tabcontainer.forEach(t -> {
			if(currentTab != t)
				list.add(tab(t));
		});
		
		tabs.setVisible(tabs.getChildren().size() != 1);
		((Hyperlink)tabs.getChildren().get(0)).fire();
		Platform.runLater(() -> ((Hyperlink)tabs.getChildren().get(0)).fire());
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
            Tab selectedTab = (Tab) node.getUserData();
			root = fillRootItem((Entry)(selectedTab.getRoot()), selectedTab == currentTab ? selectionModel.getSelectedItems() : Collections.emptyList());
			view.setRoot(root);
			return;
		}

		Entry item = (Entry)view.getSelectionModel().getSelectedItem().getValue();
		Entry parent = (Entry) item.getParent(); 
		int index = parent.indexOf(item);
		List<Entry> list = selectionModel.getSelectedItems().stream().map(e -> (Entry)e).collect(Collectors.toList());
		selectionModel.clearSelection();

		if(node == moveAbove)
			currentTab.moveChild(list, parent, index-1);
		else if(node == moveBelow)
			currentTab.moveChild(list, parent, index+1);
		else if(node == moveAsFirstChild)
			currentTab.moveChild(list, item, 0);
		else if(node == moveAsLastChild)
			currentTab.moveChild(list, item, Integer.MAX_VALUE);

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
