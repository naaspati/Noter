package sam.apps.jbook_reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import sam.apps.jbook_reader.tabs.Tab;
import sam.apps.jbook_reader.tabs.TabContainer;
import sam.fx.alert.AlertBuilder;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.properties.session.Session;

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

	public void addNewBookmark(TreeView<String> bookmarks, Tab maneger, boolean addChildBookmark) {
		TreeItem<String> item = bookmarks.getSelectionModel().getSelectedItem();
		AlertBuilder dialog = FxAlert.alertBuilder(AlertType.CONFIRMATION);
		dialog.headerText(Utils.treeToString(item, new StringBuilder("Add New "+(item == null ? "" : (addChildBookmark ? "Child": "Sibling"))+" Bookmark to\n")).toString());

		TextField tf = new TextField();
		HBox hb = new HBox(10, new Text("Title "), tf);
		dialog.content(hb);
		HBox.setHgrow(tf, Priority.ALWAYS);
		hb.setAlignment(Pos.CENTER_LEFT);

		TextArea ta = new TextArea();
		dialog.expandableContent(new VBox(10, new Text("Similar Bookmarks"), ta))
		.expanded(true);

		StringBuilder sb = new StringBuilder();

		tf.textProperty().addListener((p, o, n) -> {
			TreeItem<String>[] array = maneger.search(n, null);
			if(array == null || array.length == 0) {
				ta.setText("");
				return;
			}

			for (TreeItem<String> t : array) {
				Utils.treeToString(t, sb);
				sb.append('\n');
			}
			ta.setText(sb.toString());
			sb.setLength(0);
		});

		Platform.runLater(() -> tf.requestFocus());

		dialog.showAndWait()
		.filter(b -> b == ButtonType.OK)
		.map(b -> maneger.add(item, bookmarks.getRoot(), tf.getText(), addChildBookmark))
		.ifPresent(t -> {
			bookmarks.getSelectionModel().clearSelection();
			bookmarks.getSelectionModel().select(t);
		});
	}

	private class DeletedItem {
		final TreeItem<String> parent, child;
		final int index;
		DeletedItem(TreeItem<String> child) {
			this.parent = child.getParent();
			this.child = child;
			this.index = child.getParent().getChildren().indexOf(child);
		}
		void remove() {
			parent.getChildren().remove(index);
		}
		public void add() {
			parent.getChildren().add(index, child);
		}
	}
	private HashMap<Tab, LinkedList<DeletedItem[]>> deletedItems = new HashMap<>();
	private ReadOnlyIntegerWrapper undoDeleteSize = new ReadOnlyIntegerWrapper();
	
	public void removeBookmarkAction(TreeView<String> bookmarks, Tab currentTab) {
		if(bookmarks.getSelectionModel().getSelectedItems().isEmpty())
			return;
		
		LinkedList<DeletedItem[]> list = deletedItems.get(currentTab);
		if(list == null) {
			list = new LinkedList<>();
			deletedItems.put(currentTab, list);
		}
		DeletedItem[] ditems = bookmarks.getSelectionModel().getSelectedItems().stream()
				.map(DeletedItem::new)
				.toArray(DeletedItem[]::new);
		
		list.add(ditems);
		undoDeleteSize.set(list.size());
		
		for (DeletedItem d : ditems) d.remove();
		currentTab.setModified(true);
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
				items[i].add();
			
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
	private File getFile(String title, String suggestedName) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));

		String path = Optional.ofNullable(Session.get("last-visited-folder"))
				.orElse(Session.get("default.folder", "."));

		chooser.setInitialFileName(suggestedName);
		chooser.setInitialDirectory(new File(path));
		File file = suggestedName == null ?  chooser.showOpenDialog(Viewer.getStage()) : chooser.showSaveDialog(Viewer.getStage());
		if(file != null)
			Session.put("last-visited-folder", file.getParent());

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
				.contentText(tab.getJbookPath() != null ? tab.getJbookPath() : tab.getTabTitle())
				.buttonTypes(ButtonType.NO, ButtonType.YES, ButtonType.CANCEL)
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
		Session.put("last-visited-folder", tab.getJbookPath().getParent().toString());

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

