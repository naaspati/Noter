package sam.noter.bookmark;

import static sam.noter.Utils.fx;
import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import sam.fx.helpers.FxCell;
import sam.fx.popup.FxPopupShop;
import sam.myutils.Checker;
import sam.noter.EntryTreeItem;
import sam.noter.Utils;
import sam.noter.app.AppUtils;
import sam.noter.dao.api.IEntry;
import sam.noter.tabs.Tab;

class BookmarkAddeder extends VBox {

	@FXML private Label header;
	@FXML private VBox center;
	@FXML private TextField titleTf;
	@FXML private ListView<IEntry> similar;
	@FXML private TextArea entryPath;

	private final StringBuilder sb = new StringBuilder();
	private final WeakChangeListener<IEntry> similarSelect = new WeakChangeListener<>((p, o, n) -> {
		sb.setLength(0);
		Utils.toTreeString(n, sb);
		entryPath.setText(n == null ? null : sb.toString());
	});
	
	private final AppUtils utils;
	
	private BookmarkType bookMarkType;
	private EntryTreeItem item;
	private BookMarkTree root;
	private Runnable close;

	public BookmarkAddeder(AppUtils utils) throws IOException {
		this.utils = utils;
		similar.setCellFactory(FxCell.listCell(IEntry::getTitle));
		similar.getSelectionModel()
		.selectedItemProperty()
		.addListener(similarSelect);
	}

	private void okAction(ActionEvent e) {
		EntryTreeItem result = null;

		String s = titleTf.getText();
		if(Checker.isEmptyTrimmed(s)){
			//TODO
			FxPopupShop.showHidePopup("Invalid title", 1500);
			return;
		}

		String title = titleTf.getText();
		if(item == null)
			root.addChild( title);
		else {
			switch (bookMarkType) {
				case RELATIVE:
					result =  root.addChild(title, item.getParent(), item);
					break;
				case CHILD: 
					result =  root.addChild(title, item);
					break;
				case RELATIVE_TO_PARENT:
					result =  root.addChild(title,item.getParent().getParent(), item.getParent());
					break;
			}
		}

		if(result != null) 
			root.clearAndSelect(result);
		close();
	}

	private void close() {
		similar.getItems().clear();
		titleTf.clear();
		this.item = null;
		
		if(close != null)
			close.run();
		close = null;
	}

	public void showDialog(BookmarkType bookMarkType, BookMarkTree tree) {
		this.root = tree;

		this.bookMarkType = bookMarkType == RELATIVE_TO_PARENT && item.getParent() == tree.getRoot() ? RELATIVE : bookMarkType;
		header.setText(header());

		fx(() -> titleTf.requestFocus());
		titleTf.clear();
		
		this.item = tree.getSelectedItem(); 

		List<IEntry> list =  similar.getItems();
		list.clear();
		tree.getEntry().walk(list::add);
		
		close = utils.showDialog(this);
	}
	private String header() {
		String header = "Add new Bookmark";
		if(item != null) {
			switch (bookMarkType) {
				case RELATIVE:
					header += "\nRelative To: "+item;
					break;
				case CHILD:
					header += "\nChild To: "+item;
					break;
				case RELATIVE_TO_PARENT:
					header += "\nRelative To: "+item.getParent();
					break;
			}	
		}
		return header;
	}
}
