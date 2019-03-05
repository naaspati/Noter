package sam.noter.tabs;

import static sam.fx.helpers.FxClassHelper.setClass;
import static sam.fx.helpers.FxClassHelper.toggleClass;
import static sam.noter.Utils.chooseFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
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
import sam.noter.EntryTreeItem;
import sam.noter.Utils2.FileChooserType;
import sam.noter.dao.RootIEntryFactory;
import sam.noter.dao.Walker;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;
import sam.noter.dao.api.IRootIEntry;

public class Tab extends HBox implements IRootEntry {
	private IRootIEntry root;
	private final Label title = new Label();
	private final Button close = new Button("x");
	private final Button open = new Button("o");

	public static Tab load(Path path, Consumer<Tab> onSelect) throws Exception {
		return new Tab(RootIEntryFactory.getInstance().load(path), onSelect);
	}
	public static Tab create(Path path, Consumer<Tab> onSelect) throws Exception {
		return new Tab(RootIEntryFactory.getInstance().create(path), onSelect);
	}
	private Tab(IRootIEntry root, Consumer<Tab> onSelect) throws Exception {
		this.root = root;
		init(onSelect);
		setTabTitle(root.getJbookPath().getFileName().toString());
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

	public void setJbookPath(Path path) {
		setTabTitle(path.getFileName().toString());
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

		File path = getFile("save file", FileChooserType.SAVE);

		if(path == null)
			return ActionResult.CANCEL;

		try {
			Path file = path.toPath();
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
		
		final Path target = file.toPath();
		final Path source = getJbookPath();

		if(target.equals(source))
			return;

		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			setJbookPath(target);
		} catch (IOException e) {
			FxAlert.showErrorDialog("source: "+getJbookPath()+"\ntarget: "+target, "failed to rename", e);
		}
	}

	public File getFile(String title, FileChooserType type) {
		Path file = getJbookPath();
		File expectedDir = file == null ? null : file.getParent().toFile();

		return chooseFile(title, expectedDir, this.title.getText()+".jbook", type);
	}

	public void  open_containing_folder(HostServices hs)  {
		Optional.of(this)
		.map(Tab::getJbookPath)
		.map(Path::getParent)
		.filter(Files::exists)
		.ifPresent(p -> hs.showDocument(p.toUri().toString()));
	}

	public void  reload_from_disk()  {
		try {
			root.reload();
			FxPopupShop.showHidePopup("realoaded "+getJbookPath().getFileName(), 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialog(getJbookPath(), "failed to reload", e);
		}
	}

	protected void notifyModified() {
		toggleClass(this, "modified", root.isModified());
	}

	public IEntry getRoot() { return (IEntry)root; }

	@Override public void close() throws Exception { root.close(); }
	/**
	 * add child to root
	 * @param title
	 */
	public void addChild(String title) { addChild(title, (IEntry)root); }

	@Override public IEntry addChild(String childTitle, IEntry parent) { return root.addChild(childTitle, parent); }
	@Override public IEntry addChild(String title, IEntry parent, int index) { return root.addChild(title, parent, index); }
	@Override public Collection<IEntry> getAllEntries() { return root.getAllEntries(); }

	@Override public void setOnModified(Runnable action) { throw new IllegalAccessError(); }
	public void walkTree(Walker walker) { ((IEntry)root).walkTree(walker); }
	@Override public List<IEntry> moveChild(List<EntryTreeItem> list, EntryTreeItem parent, int index) { return root.moveChild(list, parent, index); }
	public void walk(Consumer<IEntry> consumer) { ((IEntry)root).walk(consumer); }

	@Override public Path getJbookPath() { return root.getJbookPath(); }
	@Override public boolean isModified() { return root.isModified(); }
	@Override public void reload() throws Exception { root.reload(); }
	@Override public void save(Path file) throws Exception { root.save(file); }
	@Override public void save() throws Exception { root.save(); }
	@Override public void addChild(IEntry child, IEntry parent, int index) { root.addChild(child, parent, index); }
	@Override public void removeFromParent(IEntry child) { root.removeFromParent(child); }
	@Override public void setSelectedItem(IEntry entryTreeItem) { root.setSelectedItem(entryTreeItem); }
	@Override public IEntry getSelectedItem() { return root.getSelectedItem(); }
}
