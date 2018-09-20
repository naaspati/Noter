package sam.apps.jbook_reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.apps.jbook_reader.datamaneger.Entry;
import sam.apps.jbook_reader.tabs.Tab;
import sam.apps.jbook_reader.tabs.TabContainer;
import sam.fx.alert.AlertBuilder;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;

public class Actions {
	private static transient Actions instance;

	public static Actions getInstance() {
		if (instance == null) {
			synchronized (Actions.class) {
				if (instance == null)
					instance = new Actions();
			}
		}
		return instance;
	}

	private Actions() {
	}
	public enum ActionResult {
		FAILED, SUCCESS, NULL, OK, CANCEL, YES, NO 
	}

	public void addNewBookmark(TreeView<String> bookmarks, Tab tab, BookmarkType bt1) {
		Entry item = (Entry)bookmarks.getSelectionModel().getSelectedItem();

		BookmarkType bt = bt1 == BookmarkType.RELATIVE_TO_PARENT && item.getParent() == bookmarks.getRoot() ? BookmarkType.RELATIVE : bt1;  

		String header = "Add new Bookmark";
		if(item != null) {
			switch (bt) {
				case RELATIVE:
					header += "\nRelative To: "+item.getValue();
					break;
				case CHILD:
					header += "\nChild To: "+item.getValue();
					break;
				case RELATIVE_TO_PARENT:
					header += "\nRelative To: "+item.getParent().getValue();
					break;
			}	
		}

		AlertBuilder dialog = FxAlert.alertBuilder(AlertType.CONFIRMATION)
				.title("Add New Bookmark")
				.header(header);

		TextField tf = new TextField();
		HBox hb = new HBox(10, new Text("Title "), tf);
		dialog.content(hb);
		hb.setMaxWidth(300);
		HBox.setHgrow(tf, Priority.ALWAYS);
		hb.setAlignment(Pos.CENTER_LEFT);

		TextArea ta = new TextArea();
		dialog.expandableContent(new VBox(10, new Text("Similar Bookmarks"), ta))
		.expanded(true);

		StringBuilder sb = new StringBuilder();

		tf.textProperty().addListener((p, o, n) -> {
			tab.walk()
			.filter(e -> e.getTitle() != null && e.getTitle().contains(n))
			.reduce(sb, (sb2, t) -> Utils.treeToString(t, sb2), StringBuilder::append);
			ta.setText(sb.toString());
			sb.setLength(0);
		});

		Platform.runLater(() -> tf.requestFocus());

		Dialog<ButtonType> d = dialog.build();
		d.initOwner(Main.getStage());

		d.showAndWait().filter(b -> b == ButtonType.OK)
		.map(b -> {
			String title = tf.getText();
			if(item == null)
				return Entry.cast(bookmarks.getRoot()).addChild(title, null);
			else {
				switch (bt) {
					case RELATIVE:
						return Entry.cast(item.getParent()).addChild(title, item);
					case CHILD: 
						return item.addChild(title, null);
					case RELATIVE_TO_PARENT:
						return Entry.cast(item.getParent().getParent()).addChild(title, (Entry)item.getParent());
				}
			}
			return null;
		})
		.ifPresent(t -> {
			bookmarks.getSelectionModel().clearSelection();
			bookmarks.getSelectionModel().select(t);
		});
	}
	private class PatrentChildRelation {
		final Entry parent, child;
		final int index;
		PatrentChildRelation(Entry child) {
			this.parent = (Entry) child.getParent();
			this.child = child;
			this.index = parent.indexOf(child);
		}
		void removeChildFromParent() {
			parent.remove(child);
		}
		public void addChildToParent() {
			parent.add(index, child);
		}
	}
	private HashMap<Tab, LinkedList<PatrentChildRelation[]>> deletedItems = new HashMap<>();
	private ReadOnlyIntegerWrapper undoDeleteSize = new ReadOnlyIntegerWrapper();

	public void removeBookmarkAction(TreeView<String> bookmarks, Tab currentTab) {
		if(bookmarks.getSelectionModel().getSelectedItems().isEmpty())
			return;

		LinkedList<PatrentChildRelation[]> list = deletedItems.get(currentTab);
		if(list == null) {
			list = new LinkedList<>();
			deletedItems.put(currentTab, list);
		}
		PatrentChildRelation[] ditems = bookmarks.getSelectionModel().getSelectedItems().stream()
				.map(Entry::cast)
				.map(PatrentChildRelation::new)
				.toArray(PatrentChildRelation[]::new);

		list.add(ditems);
		undoDeleteSize.set(list.size());

		for (PatrentChildRelation d : ditems) d.removeChildFromParent();
	}
	public ReadOnlyIntegerProperty undoDeleteSizeProperty() {
		return undoDeleteSize.getReadOnlyProperty();
	}
	public void tabClosed(Tab tab) {
		deletedItems.remove(tab);
	}
	public void undoRemoveBookmark(Tab tab) {
		if(undoDeleteSize.get() == 0)
			return;

		Optional.ofNullable(tab)
		.map(deletedItems::get)
		.map(LinkedList::pollLast)
		.ifPresent(items -> {
			for (int i = items.length - 1; i >= 0 ; i--)
				items[i].addChildToParent();

			undoDeleteSize.set(deletedItems.get(tab).size());
		});

	}
	public void switchTab(Tab newTab) {
		int size = Optional.ofNullable(newTab)
				.map(deletedItems::get)
				.map(List::size)
				.orElse(0);

		undoDeleteSize.set(size);
	}
	public void moveBookmarks(TreeView<String> bookmarks, List<TreeItem<String>> selectedItems) {
		TreeItem<TreeItem<String>> root = fillRootItem(bookmarks.getRoot(), selectedItems);
		TreeView<TreeItem<String>> view = new TreeView<>(root);
		view.setShowRoot(false);
		view.setCellFactory(tt -> new TreeCell<TreeItem<String>>() {
			@Override
			protected void updateItem(TreeItem<String> item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty ? null : item.getValue());
			}
		});

		Stage stg = new Stage(StageStyle.UNIFIED);
		stg.initModality(Modality.APPLICATION_MODAL);
		stg.initOwner(Main.getStage());

		Button moveAbove = new Button("Move Above");
		Button moveBelow = new Button("Move Below");
		Button moveAsFirstChild = new Button("Move As First Child");
		Button moveAsLastChild = new Button("Move As Last Child");

		EventHandler<ActionEvent> action = e -> {
			Entry item = (Entry)view.getSelectionModel().getSelectedItem().getValue();
			Entry parent = (Entry) item.getParent(); 
			int index = parent.indexOf(item);
			List<TreeItem<String>> list = new ArrayList<>(selectedItems);
			list.forEach(t -> ((Entry)t.getParent()).remove(t));

			if(e.getSource() == moveAbove){
				if(index == 0)
					parent.addAll(0, list);
				else
					parent.addAll(index, list);
			}
			else if(e.getSource() == moveBelow)
				parent.addAll(index, list);
			else if(e.getSource() == moveAsFirstChild)
				item.addAll(0, list);
			else if(e.getSource() == moveAsLastChild)
				item.addAll(list);

			stg.hide();
			bookmarks.getSelectionModel().clearSelection();
			bookmarks.getSelectionModel().select(list.get(0));
		};

		moveAbove.setOnAction(action);
		moveBelow.setOnAction(action);
		moveAsFirstChild.setOnAction(action);
		moveAsLastChild.setOnAction(action);

		VBox buttons = new VBox(10, moveAbove,moveBelow,moveAsFirstChild,moveAsLastChild);
		buttons.setPadding(new Insets(10));
		buttons.setAlignment(Pos.CENTER);
		buttons.disableProperty().bind(view.getSelectionModel().selectedItemProperty().isNull());

		view.setId("bookmarks");
		Scene scene = new Scene(new HBox(view,buttons));
		scene.getStylesheets().add("style.css");
		stg.setScene(scene);
		stg.setResizable(false);
		stg.showAndWait();
	}
	private TreeItem<TreeItem<String>> fillRootItem(TreeItem<String> root, List<TreeItem<String>> selectedItems) {
		TreeItem<TreeItem<String>> item = new TreeItem<>(root);
		for (TreeItem<String> ti : root.getChildren()) {
			if(!selectedItems.contains(ti))
				item.getChildren().add(fillRootItem(ti, selectedItems));
		}
		return item;
	}

	private File getFile(String title, String suggestedName) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));

		final Path p = Main.CONFIG_DIR.resolve("last-visited-folder.txt");

		String path;
		try {
			path = Files.exists(p) ? new String(Files.readAllBytes(p)) : null;
		} catch (IOException e) {
			path = null;
		}

		chooser.setInitialFileName(suggestedName);
		File file = path == null ? null : new File(path);
		if(file != null && file.exists())
			chooser.setInitialDirectory(file);
		else
			chooser.setInitialDirectory(Main.APP_HOME.toFile());
		file = suggestedName == null ?  chooser.showOpenDialog(Main.getStage()) : chooser.showSaveDialog(Main.getStage());

		if(file != null) {
			try {
				Files.write(p, file.getParent().toString().replace('\\', '/').getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {}
		}

		return file;
	}
	public void open(TabContainer tabsContainer, Path jbookPath, Menu recentsMenu)  {
		if(jbookPath == null) {
			File file = getFile("select a file to open...", null);

			if(file == null)
				return;

			jbookPath = file.toPath();
		}
		tabsContainer.addTab(jbookPath);

		Path p = jbookPath;

		recentsMenu.getItems()
		.removeIf(mi -> p.equals(mi.getUserData()));
	}
	public void  open_containing_folder(HostServices hs, Tab tab)  {
		Optional.of(tab)
		.map(Tab::getJbookPath)
		.map(Path::getParent)
		.filter(Files::exists)
		.ifPresent(p -> hs.showDocument(p.toUri().toString()));
	}
	public void  reload_from_disk(Tab tab)  {
		if(tab == null)
			return;

		try {
			tab.reload();
			FxPopupShop.showHidePopup("realoaded "+tab.getJbookPath().getFileName(), 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialog(tab.getJbookPath(), "failed to reload", e);
		}
	}
	public ActionResult  save(Tab tab, boolean confirmBeforeSaving)  {
		if(tab == null)
			return ActionResult.NULL;

		if(confirmBeforeSaving) {
			ActionResult ar = confirmSaving("Save As", tab);
			if(ar != ActionResult.YES)
				return ar;
		}

		if(tab.getJbookPath() == null)
			return save_as(tab, false);

		try {
			tab.save();
		} catch (Exception e) {
			FxAlert.showErrorDialog(tab.getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}

		FxPopupShop.showHidePopup("file saved", 1500);
		return ActionResult.SUCCESS;
	}
	private ActionResult confirmSaving(String title, Tab tab) {
		return
				FxAlert.alertBuilder(AlertType.CONFIRMATION)
				.title("Save File")
				.content(tab.getJbookPath() != null ? tab.getJbookPath() : tab.getTabTitle())
				.buttons(ButtonType.NO, ButtonType.YES, ButtonType.CANCEL)
				.showAndWait()
				.map(b -> b == ButtonType.YES ? ActionResult.YES : 
					b == ButtonType.NO ? ActionResult.NO : 
						ActionResult.CANCEL)
				.orElse(ActionResult.NULL);
	}
	public ActionResult  save_as(Tab tab, boolean confirmBeforeSaving)  {
		if(tab == null)
			return ActionResult.NULL;

		if(confirmBeforeSaving) {
			ActionResult ar = confirmSaving("Save As", tab);
			if(ar != ActionResult.YES)
				return ar;
		}

		File file = getFile("save file", tab.getTabTitle());

		if(file == null)
			return ActionResult.CANCEL;

		try {
			tab.save(file.toPath());
			tab.setJbookPath(file.toPath());
		} catch (Exception e) {
			FxAlert.showErrorDialog(tab.getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}
		return ActionResult.SUCCESS;
	}
	public void  rename(Tab tab)  {
		File file = getFile("rename", tab.getJbookPath().getFileName().toString());
		if(file == null) {
			FxPopupShop.showHidePopup("cancelled", 1500);
			return;
		}
		try {
			tab.setJbookPath(Files.move(tab.getJbookPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING));
		} catch (IOException e) {
			FxAlert.showErrorDialog("source: "+tab.getJbookPath()+"\ntarget: "+file, "failed to rename", e);
		}
	}
}

