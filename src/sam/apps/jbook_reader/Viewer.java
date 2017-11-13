package sam.apps.jbook_reader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.When;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import sam.apps.jbook_reader.tabs.Tab;
import sam.apps.jbook_reader.tabs.TabContainer;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.properties.session.Session;

public class Viewer extends Application {
	public static void main( String[] args ) {
		launch(args);
	}

	private final TreeView<String> bookmarks = new TreeView<>();
	private final Editor editor = new Editor();
	private final TabContainer tabsContainer = new TabContainer();
	private SimpleObjectProperty<Tab> currentTab = new SimpleObjectProperty<>();
	private SimpleObjectProperty<Path> currentFile = new SimpleObjectProperty<>();
	private static Stage stage;

	public static Stage getStage() {
		return stage;
	}
	@Override
	public void start(Stage stage) throws Exception {
		Viewer.stage = stage;
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);

		SplitPane splitPane = new SplitPane(getBookmarkPane(), getEditor());
		splitPane.setDividerPosition(0, 0.2);
		splitPane.widthProperty().addListener((p, o, n) -> splitPane.setDividerPosition(0, 0.2));
		tabsContainer.setOnTabSwitch(this::switchTab);
		tabsContainer.setOnTabClosing(tab -> {
			if(getCurrentTab() == null)
				return;
			getCurrentTab().setContent(getSelectedItem(), editor.getContent());
		});
		editor.disableIf(currentTab.isNull());

		BorderPane root = new BorderPane(splitPane);
		root.setTop(getMenubar());

		Scene scene = new Scene(root);
		stage.setScene(scene);
		scene.getStylesheets().add("style.css");

		if(Session.has("stage.width")){
			stage.setWidth(Double.parseDouble(Session.get("stage.width")));
			stage.setHeight(Double.parseDouble(Session.get("stage.height")));
			stage.setX(Double.parseDouble(Session.get("stage.x")));
			stage.setY(Double.parseDouble(Session.get("stage.y")));
		}
		else 
			stage.setMaximized(true);

		stage.show();
		stage.setOnCloseRequest(e -> {
			exit();
			e.consume();
		});
	}

	private TreeItem<String> getSelectedItem() {
		return bookmarks.getSelectionModel().getSelectedItem();
	}
	private Tab getCurrentTab() {
		return currentTab.get();
	}

	private Node getEditor() {
		return new BorderPane(editor.getContentNode(), tabsContainer, null, null, null);
	}
	private void exit() {
		Session.put("stage.width", String.valueOf(stage.getWidth()));
		Session.put("stage.height", String.valueOf(stage.getHeight()));
		Session.put("stage.x", String.valueOf(stage.getX()));
		Session.put("stage.y", String.valueOf(stage.getY()));

		if(tabsContainer.closeAll())
			System.exit(0);
	}
	private Node getMenubar() {
		MenuBar menubar = new MenuBar(getFileMenu());
		return menubar;
	}

	private Menu getFileMenu() {
		BooleanBinding fileNull = currentFile.isNull();
		BooleanBinding tabNull = currentTab.isNull();
		BooleanBinding isZero = tabsContainer.tabsCountProperty().isEqualTo(0);

		Menu closeSpecific;

		Menu menu = new Menu("_File", null, 
				menuitem("_New", new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), e -> tabsContainer.addBlankTab()),
				menuitem("_Open...", new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), e -> Actions.open(tabsContainer)),
				menuitem("Open Containing Folder", e -> Actions.open_containing_folder(getHostServices(), getCurrentTab()), fileNull),
				menuitem("Reload From Disk", e -> Actions.reload_from_disk(getCurrentTab()), fileNull),
				menuitem("_Save", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), e -> Actions.save(getCurrentTab(), false), fileNull),
				menuitem("Save _As", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> {Actions.save_as(getCurrentTab(), false); updateTab();}, tabNull),
				menuitem("Sav_e All", new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN), e -> {Actions.save_all(tabsContainer); updateTab();}, tabNull),
				menuitem("Rename", e -> {Actions.rename(getCurrentTab()); updateTab();}, fileNull),
				new SeparatorMenuItem(),
				menuitem("_Close",new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), e -> tabsContainer.closeTab(getCurrentTab()), isZero),
				menuitem("Close All",new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), e -> tabsContainer.closeAll(), isZero),
				closeSpecific = 
				new Menu("Close specific", null,
						menuitem("other tab(s)", e -> tabsContainer.closeExcept(getCurrentTab())),
						menuitem("tab(s) to the right", e -> tabsContainer.closeRightLeft(getCurrentTab(), true)),
						menuitem("tab(s) to the left", e -> tabsContainer.closeRightLeft(getCurrentTab(), false))
						),
				new SeparatorMenuItem(),
				menuitem("E_xit", new KeyCodeCombination(KeyCode.F4, KeyCombination.ALT_DOWN), e -> exit())
				);
		closeSpecific.disableProperty().bind(tabsContainer.tabsCountProperty().lessThan(2));
		return menu;
	}
	private void updateTab() {
		Tab t = getCurrentTab(); 
		if(t == null)
			return;
		
		currentFile.set(t.getJbookPath());
		stage.setTitle(t.getTitle());
	}
	private void switchTab(final Tab newTab) {
		if(getCurrentTab() != null)
			getCurrentTab().setContent(getSelectedItem(), editor.getContent());

		currentTab.set(newTab);

		if(newTab == null) {
			stage.setTitle(null);
			bookmarks.setRoot(null);
			editor.setContent(null);
			currentFile.set(null);
		}
		else {
			currentFile.set(newTab.getJbookPath());
			stage.setTitle(newTab.getTitle());
			bookmarks.setRoot(newTab.getRootItem());
			editor.setContent(newTab.getContent(getSelectedItem()));
		}
	}

	private MenuItem menuitem(String label, EventHandler<ActionEvent> action) {
		return menuitem(label, null, action);
	}
	private MenuItem menuitem(String label, EventHandler<ActionEvent> action, BooleanBinding disable) {
		return menuitem(label, null, action, disable);
	}
	private MenuItem menuitem(String label, KeyCombination accelerator, EventHandler<ActionEvent> action) {
		return menuitem(label, accelerator, action, null);
	}
	private MenuItem menuitem(String label, KeyCombination accelerator, EventHandler<ActionEvent> action, BooleanBinding disable) {
		MenuItem mi = new MenuItem(label);

		if(accelerator != null)
			mi.setAccelerator(accelerator);
		if(action != null)
			mi.setOnAction(action);
		if(disable != null)
			mi.disableProperty().bind(disable);

		return mi;
	}

	private Node getBookmarkPane() throws IOException {
		bookmarks.setShowRoot(false);
		bookmarks.setEditable(true);
		bookmarks.setStyle("-fx-font-family:Consolas");
		bookmarks.setCellFactory(TextFieldTreeCell.forTreeView());
		bookmarks.setOnEditCommit(e -> getCurrentTab().setTitle(e.getTreeItem(), e.getNewValue()));
		bookmarks.getSelectionModel().selectedItemProperty()
		.addListener((p, o, n) -> {
			if(getCurrentTab() != null ) {
				getCurrentTab().setContent(o, editor.getContent());
				editor.setContent(getCurrentTab().getContent(n));
			}
			if(n != null)
				editor.setTitle(n.getValue());
		});

		BorderPane pane = new BorderPane(bookmarks);

		Button show = button("show","Chevron Right_20px.png", null);
		VBox showBox = new VBox(show);
		showBox.setMaxWidth(20);
		showBox.setPadding(new Insets(5, 0, 0, 0));

		EventHandler<ActionEvent> showHideAction = e -> {
			boolean isShow = e.getSource() == show;
			Node node = isShow ? showBox : pane;
			SplitPane container = (SplitPane)(node.getParent().getParent());
			List<Node> list = container.getItems();
			list.set(list.indexOf(node), isShow ? pane : showBox);
			container.setDividerPositions(0.3);
		};
		show.setOnAction(showHideAction);

		RadioButton expandCollpase = new RadioButton();
		expandCollpase.tooltipProperty().bind(new When(expandCollpase.selectedProperty()).then(new Tooltip("collapse")).otherwise(new Tooltip("expand")));
		expandCollpase.setPrefHeight(20);
		expandCollpase.setPrefWidth(20);
		expandCollpase.setOnAction(e -> getCurrentTab().setExpanded(expandCollpase.isSelected()));

		Pane p;
		Button removeButton;
		HBox controls = new HBox(3, 
				button("add", "Plus_20px.png", e -> Actions.addAction(bookmarks, getCurrentTab())),
				removeButton = button("remove selected","Cancel_20px.png", e -> Actions.removeAction(bookmarks, getCurrentTab())),
				expandCollpase,
				p = new Pane(),
				button("hide","Chevron Left_20px.png", showHideAction)
				);

		HBox.setHgrow(p, Priority.ALWAYS);

		removeButton.graphicProperty().bind(new When(removeButton.disableProperty()).then(new ImageView("Cancel-disabled_20px.png")).otherwise(new ImageView("Cancel_20px.png")));
		removeButton.disableProperty()
		.bind(bookmarks.getSelectionModel().selectedItemProperty().isNull());

		controls.setPadding(new Insets(5));
		pane.setTop(controls);
		pane.disableProperty().bind(currentTab.isNull());
		return pane;
	}

	private Button button(String tooltip, String iconName, EventHandler<ActionEvent> action) {
		Button b = new Button(null, new ImageView(iconName));
		b.getStyleClass().clear();
		b.setTooltip(new Tooltip(tooltip));

		if(action != null)
			b.setOnAction(action);

		return b;
	}
}
