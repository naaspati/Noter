package sam.noter.tabs;

import static sam.fx.helpers.FxClassHelper.setClass;
import static sam.fx.helpers.FxClassHelper.toggleClass;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.application.HostServices;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.noter.ActionResult;
import sam.noter.Utils;
import sam.noter.datamaneger.DataManeger;

public class Tab extends DataManeger {
	private final HBox view = new HBox(5);
	private final Label title = new Label();
	private final Button close = new Button("x");
	private final Button open = new Button("o");

	public Tab(File path, Consumer<Tab> onSelect) throws Exception {
		super(path);
		init(onSelect);
		setTabTitle(path.getName());
	}

	public Tab(Consumer<Tab> onSelect) throws Exception {
		super();
		init(onSelect);
	}
	private void init(Consumer<Tab> onSelect) {
		view.getChildren().addAll(title, close);
		title.setMaxWidth(70);

		setClass(view, "tab");
		setClass(title, "title");
		setClass(close, "close");
		setClass(open, "open");
		
		open.setOnAction(e -> FileOpenerNE.openFile((File)open.getUserData()));
		close.setTooltip(new Tooltip("close tab"));
		
		view.setOnMouseClicked(e -> {
			if(e.getButton() == MouseButton.PRIMARY)
				onSelect.accept(this);
		});
	}
	
	public HBox getView() { return view; }

	public void setTabTitle(String string) { 
		title.setText(string);
		title.setTooltip(new Tooltip(string));
		}
	public String getTabTitle() { return title.getText(); }
	public void setOnClose(Consumer<Tab> action) { close.setOnAction(e -> action.accept(Tab.this)); }

	@Override
	protected void setModified(boolean b) {
		super.setModified(b);
		if(view == null)
			return;
		toggleClass(view, "modified", isModified());
	}
	@Override
	public void setJbookPath(File path) {
		setTabTitle(path.getName());
		super.setJbookPath(path);
	}
	public boolean isActive() {
		return view.getStyleClass().contains("active");
	}
	public void setActive(boolean b) {
		toggleClass(view, "active", b);
	}
	public void setContextMenu(ContextMenu cm) {
		view.setOnContextMenuRequested(e -> {
			cm.setUserData(Tab.this);
			cm.show(view, Side.BOTTOM, 0, 0);
		});
	}
	public void setBoundBook(File file) {
		if(file == null) 
			view.getChildren().remove(open);
		else {
			open.setTooltip(new Tooltip("open bound book: "+file.getName()));
			open.setUserData(file);
			if(!view.getChildren().contains(open))
				view.getChildren().add(1,open);
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
			save();
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

		File file = Utils.getFile("save file", getTabTitle());

		if(file == null)
			return ActionResult.CANCEL;

		try {
			save(file);
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
		File file = Utils.getFile("rename", getJbookPath().getName());
		if(file == null) {
			FxPopupShop.showHidePopup("cancelled", 1500);
			return;
		}
		try {
			setJbookPath(Files.move(getJbookPath().toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile());
		} catch (IOException e) {
			FxAlert.showErrorDialog("source: "+getJbookPath()+"\ntarget: "+file, "failed to rename", e);
		}
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
			reload();
			FxPopupShop.showHidePopup("realoaded "+getJbookPath().getName(), 1500);
		} catch (Exception e) {
			FxAlert.showErrorDialog(getJbookPath(), "failed to reload", e);
		}
	}
	
}
