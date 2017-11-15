package sam.apps.jbook_reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
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
import sam.apps.jbook_reader.datamaneger.DataManeger;
import sam.apps.jbook_reader.tabs.Tab;
import sam.apps.jbook_reader.tabs.TabContainer;
import sam.fx.alert.AlertBuilder;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.properties.session.Session;

public class Actions {
	public enum ActionResult {
		FAILED, SUCCESS, NULL, OK, CANCEL, YES, NO 
	}

	public static void addNewTab(TreeView<String> bookmarks, Tab maneger, boolean addChildBookmark) {
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
	private static File getFile(String title, String suggestedName) {
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
	public static void removeAction(TreeView<String> bookmarks, DataManeger maneger) {
		maneger.remove(bookmarks.getSelectionModel().getSelectedItems(), bookmarks.getRoot());
		bookmarks.getSelectionModel().clearSelection();
	}
	public static void  open(TabContainer tabContainer)  {
		File file = getFile("select a file to open...", null);

		if(file == null)
			return;

		tabContainer.addTab(file.toPath()); 
	}
	public static void  open_containing_folder(HostServices hs, Tab tab)  {
		Optional.of(tab)
		.map(Tab::getJbookPath)
		.map(Path::getParent)
		.filter(Files::exists)
		.ifPresent(p -> hs.showDocument(p.toUri().toString()));
	}
	public static void  reload_from_disk(Tab tab, TreeItem<String> rootItem)  {
		if(tab == null)
			return;

		try {
			rootItem.getChildren().setAll(tab.reload());
			FxPopupShop.showHidePopup("realoaded "+tab.getJbookPath().getFileName(), 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialoag(tab.getJbookPath(), "failed to reload", e);
		}
	}
	public static ActionResult  save(Tab tab, boolean confirmBeforeSaving)  {
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
			FxAlert.showErrorDialoag(tab.getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}

		FxPopupShop.showHidePopup("file saved", 1500);
		return ActionResult.SUCCESS;
	}
	private static ActionResult confirmSaving(String title, Tab tab) {
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
	public static ActionResult  save_as(Tab tab, boolean confirmBeforeSaving)  {
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
			FxAlert.showErrorDialoag(tab.getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}
		return ActionResult.SUCCESS;
	}
	public static void  rename(Tab tab)  {
		Session.put("last-visited-folder", tab.getJbookPath().getParent().toString());

		File file = getFile("rename", tab.getJbookPath().getFileName().toString());
		if(file == null) {
			FxPopupShop.showHidePopup("cancelled", 1500);
			return;
		}
		try {
			tab.setJbookPath(Files.move(tab.getJbookPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING));
		} catch (IOException e) {
			FxAlert.showErrorDialoag("source: "+tab.getJbookPath()+"\ntarget: "+file, "failed to rename", e);
		}
	}
}

