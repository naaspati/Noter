package sam.apps.jbook_reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.application.HostServices;
import javafx.geometry.Pos;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
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
import sam.myutils.renamer.RemoveInValidCharFromString;
import sam.properties.session.Session;

public class Actions {
	public enum ActionResult {
		FAILED, CANCELLED, SUCCESS, NULL
	}

	public static void addAction(TreeView<String> bookmarks, DataManeger maneger) {
		AlertBuilder dialog = FxAlert.alertBuilder(AlertType.CONFIRMATION);
		dialog.headerText(treeToString(bookmarks.getSelectionModel().getSelectedItem(), new StringBuilder("Add New Bookmark to\n")).toString());

		TextField tf = new TextField();
		HBox hb = new HBox(10, new Text("Title "), tf);
		dialog.content(hb);
		HBox.setHgrow(tf, Priority.ALWAYS);
		hb.setAlignment(Pos.CENTER_LEFT);

		TextArea ta = new TextArea();
		dialog.expandableContent(new VBox(10, new Text("Matching Entries"), ta))
		.expanded(true);

		StringBuilder sb = new StringBuilder();

		tf.textProperty().addListener((p, o, n) -> {
			TreeItem<String>[] array = maneger.search(n);
			if(array == null || array.length == 0) {
				ta.setText("");
				return;
			}

			for (TreeItem<String> item : array) {
				treeToString(item, sb);
				sb.append('\n');
			}
			ta.setText(sb.toString());
			sb.setLength(0);
		});

		dialog.showAndWait()
		.filter(b -> b == ButtonType.OK)
		.ifPresent(b -> {
			TreeItem<String> parent = bookmarks.getSelectionModel().getSelectedItem();
			if(parent == null)
				parent = bookmarks.getRoot();

			bookmarks.getSelectionModel().select(maneger.add(parent, tf.getText()));
		});
	}

	static final char[] separator2 = {' ', '>', ' '};
	static StringBuilder treeToString(TreeItem<String> item, StringBuilder sb) {
		if(item == null)
			return sb;

		List<String> list = new ArrayList<>();
		TreeItem<String> t = item;
		list.add(t.getValue());

		while((t = t.getParent()) != null) list.add(t.getValue());

		for (int i = list.size() - 2; i >= 0 ; i--)
			sb.append(list.get(i)).append(separator2);

		sb.setLength(sb.length() - 3);
		return sb;
	}

	static File getFile(String title, String suggestedName) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));

		File initial = 
				Optional.ofNullable(
						Optional.ofNullable(Session.get("last-visited-folder"))
						.orElse(Session.get("default.folder"))
						)
				.map(File::new)
				.orElse(new File("."));

		chooser.setInitialFileName(suggestedName);
		chooser.setInitialDirectory(initial);
		File file = suggestedName == null ?  chooser.showOpenDialog(Viewer.getStage()) : chooser.showSaveDialog(Viewer.getStage());
		if(file != null)
			Session.put("last-visited-folder", file.getParent());

		return file;
	}
	public static void removeAction(TreeView<String> bookmarks, DataManeger maneger) {
		maneger.remove(bookmarks.getSelectionModel().getSelectedItems());
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
	public static void  reload_from_disk(Tab tab)  {
		if(tab == null)
			return;

		try {
			tab.reload();
			FxPopupShop.showHidePopup("realoaded "+tab.getJbookPath().getFileName(), 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialoag(tab.getJbookPath(), "failed to reload", e);
		}
	}
	public static ActionResult  save(Tab tab, boolean confirmBeforeSaving)  {
		if(tab == null)
			return ActionResult.NULL;

		if(confirmBeforeSaving && !confirmSaving("save", tab))
			return ActionResult.CANCELLED;

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
	private static boolean confirmSaving(String title, Tab tab) {
		Optional<ButtonType> button = FxAlert.showConfirmDialog(title, "Save File", tab.getJbookPath() != null ? tab.getJbookPath() : tab.getTitle()).showAndWait();
		return button.isPresent() && button.get() == ButtonType.OK;
	}
	public static ActionResult  save_as(Tab tab, boolean confirmBeforeSaving)  {
		if(tab == null)
			return ActionResult.NULL;

		if(confirmBeforeSaving && !confirmSaving("save", tab))
			return ActionResult.CANCELLED;

		File file = getFile("save file", tab.getTitle());

		if(file == null)
			return ActionResult.CANCELLED;

		try {
			tab.save(file.toPath());
			tab.setJbookPath(file.toPath());
		} catch (Exception e) {
			FxAlert.showErrorDialoag(tab.getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}
		return ActionResult.SUCCESS;
	}
	public static void  save_all(TabContainer container)  {
		container.saveAllTabs();
	}
	public static void  rename(Tab tab)  {
		final String initial = tab.getJbookPath().getFileName().toString();
		TextInputDialog dialog = new TextInputDialog(initial);
		dialog.initOwner(Viewer.getStage());
		dialog.setHeaderText("Rename File");
		dialog.setContentText("new name"); 

		Optional<String> strOP = dialog.showAndWait();

		if(!strOP.isPresent())
			return;

		String str = strOP.get();

		if(initial.equals(str))
			return;

		str = RemoveInValidCharFromString.removeInvalidCharsFromFileName(str);

		if(str == null || str.isEmpty()) {
			FxPopupShop.showHidePopup("bad name", 1500);
			return;
		}

		if(initial.equals(str))
			return;

		try {
			tab.setJbookPath(Files.move(tab.getJbookPath(), tab.getJbookPath().resolveSibling(str), StandardCopyOption.REPLACE_EXISTING));
		} catch (IOException e) {
			FxAlert.showErrorDialoag(tab.getJbookPath()+"\n"+tab.getJbookPath().resolveSibling(str), "failed to rename", e);
		}
	}
}

