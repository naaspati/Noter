package sam.noter.tabs;

import static sam.fx.helpers.FxClassHelper.setClass;
import static sam.fx.helpers.FxClassHelper.toggleClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.application.HostServices;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.noter.ActionResult;
import sam.noter.Utils;
import sam.noter.Utils.FileChooserType;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;
import sam.noter.dao.RootEntryFactory;
import sam.noter.dao.Walker;

public class Tab extends HBox implements RootEntry {
	private final RootEntry root;
	private final Label title = new Label();
	private final Button close = new Button("x");
	private final Button open = new Button("o");

	public Tab(File path, Consumer<Tab> onSelect) throws Exception {
		root = RootEntryFactory.getInstance().load(path);
		init(onSelect);
		setTabTitle(path.getName());
	}
	public Tab(Consumer<Tab> onSelect) throws Exception {
		root = RootEntryFactory.getInstance().create();
		init(onSelect);
	}
	private void init(Consumer<Tab> onSelect) {
		setSpacing(5);
		getChildren().addAll(title,new Separator(Orientation.VERTICAL), open, close);
		root.setOnModified(this::notifyModified);

		setClass(this, "tab");
		setClass(title, "title");
		setClass(close, "close");
		setClass(open, "open");

		open.setOnAction(e -> FileOpenerNE.openFile((File)open.getUserData()));
		close.setTooltip(new Tooltip("close tab"));

		setOnMouseClicked(e -> {
			if(e.getButton() == MouseButton.PRIMARY)
				onSelect.accept(this);
		});
	}

	public void setTabTitle(String string) { 
		title.setText(string.replace(".jbook", ""));
		title.setTooltip(new Tooltip(string));
	}
	public String getTabTitle() { return title.getText(); }
	public void setOnClose(Consumer<Tab> action) { close.setOnAction(e -> action.accept(Tab.this)); }

	public void setJbookPath(File path) {
		setTabTitle(path.getName());
		root.setJbookPath(path);
	}
	public boolean isActive() {
		return getStyleClass().contains("active");
	}
	public void setActive(boolean b) {
		toggleClass(this, "active", b);
	}
	public void setContextMenu(ContextMenu cm) {
		setOnContextMenuRequested(e -> {
			cm.setUserData(Tab.this);
			cm.show(this, Side.BOTTOM, 0, 0);
		});
	}
	public void setBoundBook(File file) {
		if(file == null) 
			open.setVisible(false);
		else {
			open.setVisible(true);
			open.setTooltip(new Tooltip("open bound book: "+file.getName()));
			open.setUserData(file);
		}
	}	

	public ActionResult save(boolean confirmBeforeSaving)  {
		if(confirmBeforeSaving) {
			ActionResult ar = confirmSaving("Save As");
			if(ar != ActionResult.YES)
				return ar;
		}

		if(getJbookPath() == null)
			return save_as(false);

		try {
			root.save();
		} catch (Exception e) {
			FxAlert.showErrorDialog(getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}

		FxPopupShop.showHidePopup("file saved", 1500);
		return ActionResult.SUCCESS;
	}
	public ActionResult  save_as(boolean confirmBeforeSaving)  {
		if(confirmBeforeSaving) {
			ActionResult ar = confirmSaving("Save As");
			if(ar != ActionResult.YES)
				return ar;
		}

		File file = getFile("save file", FileChooserType.SAVE);

		if(file == null)
			return ActionResult.CANCEL;

		try {
			root.save(file);
			setJbookPath(file);
		} catch (Exception e) {
			FxAlert.showErrorDialog(getJbookPath(), "failed to save", e);
			return ActionResult.FAILED;
		}
		return ActionResult.SUCCESS;
	}

	private ActionResult confirmSaving(String title) {
		return
				FxAlert.alertBuilder(AlertType.CONFIRMATION)
				.title("Save File")
				.content(getJbookPath() != null ? getJbookPath() : getTabTitle())
				.buttons(ButtonType.NO, ButtonType.YES, ButtonType.CANCEL)
				.showAndWait()
				.map(b -> b == ButtonType.YES ? ActionResult.YES : 
					b == ButtonType.NO ? ActionResult.NO : 
						ActionResult.CANCEL)
				.orElse(ActionResult.NULL);
	}

	public void  rename()  {
		File file = getFile("rename", FileChooserType.SAVE);
		if(file == null) {
			FxPopupShop.showHidePopup("cancelled", 1500);
			return;
		}

		if(Objects.equals(getJbookPath(), file))
			return;

		try {
			Files.move(getJbookPath().toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
			setJbookPath(file);
		} catch (IOException e) {
			FxAlert.showErrorDialog("source: "+getJbookPath()+"\ntarget: "+file, "failed to rename", e);
		}
	}

	public File getFile(String title, FileChooserType type) {
		File file = getJbookPath();
		File expectedDir = file == null ? null : file.getParentFile();

		return Utils.chooseFile(title, expectedDir, this.title.getText()+".jbook", type);
	}

	public void  open_containing_folder(HostServices hs)  {
		Optional.of(this)
		.map(Tab::getJbookPath)
		.map(File::getParentFile)
		.filter(File::exists)
		.ifPresent(p -> hs.showDocument(p.toPath().toUri().toString()));
	}

	public void  reload_from_disk()  {
		try {
			root.reload();
			FxPopupShop.showHidePopup("realoaded "+getJbookPath().getName(), 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialog(getJbookPath(), "failed to reload", e);
		}
	}

	protected void notifyModified() {
		toggleClass(this, "modified", root.isModified());
	}

	public RootEntry getRoot() { return root; }

	@Override public void close() throws Exception { root.close(); }
	/**
	 * add child to root
	 * @param title
	 */
	public void addChild(String title) { addChild(title, (Entry)root); }

	@Override public void setTitle(Entry entry, String title) { root.setTitle(entry, title); }
	@Override public void setContent(Entry entry, String content) { root.setContent(entry, content); }
	@Override public Entry addChild(String childTitle, Entry parent) { return root.addChild(childTitle, parent); }
	@Override public Entry addChild(String title, Entry parent, int index) { return root.addChild(title, parent, index); }
	@Override public Map<Integer, Entry> getEntriesMap() { return root.getEntriesMap(); }

	@Override public void setOnModified(Runnable action) { throw new IllegalAccessError(); }
	public void walkTree(Walker walker) { ((Entry)root).walkTree(walker); }
	@Override public List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index) { return root.moveChild(childrenToMove, newParent, index); }
	public void walk(Consumer<Entry> consumer) { ((Entry)root).walk(consumer); }

	@Override public File getJbookPath() { return root.getJbookPath(); }
	@Override public boolean isModified() { return root.isModified(); }
	@Override public void reload() throws Exception { root.reload(); }
	@Override public void save(File file) throws Exception { root.save(file); }
	@Override public void save() throws Exception { root.save(); }


}
