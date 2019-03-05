package sam.noter.bookmark;

import static sam.noter.Utils2.fx;
import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import java.io.IOException;
import java.util.Collection;

import javax.inject.Provider;

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
import sam.di.Injector;
import sam.fx.helpers.FxCell;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.myutils.Checker;
import sam.noter.Utils2;
import sam.noter.dao.api.IEntry;
import sam.noter.tabs.Tab;

class BookmarkAddeder {
	
	@FXML private Label header;
	@FXML private VBox center;
	@FXML private TextField titleTf;
	@FXML private ListView<IEntry> similar;
	@FXML private TextArea entryPath;
	
	private final TitleSearch search = new TitleSearch();
	private final WeakChangeListener<String> searcher = new WeakChangeListener<>((p, o, n) -> search.addSearch(n));
	private Tab tab;
	private final WeakChangeListener<IEntry> similarSelect = new WeakChangeListener<>((p, o, n) -> entryPath.setText(n == null ? null : Utils2.toTreeString(n)));
	private BookmarkType bookMarkType;
	private IEntry item;
	private final Provider<Injector> injector;

	public BookmarkAddeder(Provider<Injector> injector) throws IOException {
		this.injector = injector;
		FxFxml.load(this, true);

		similar.setCellFactory(FxCell.listCell(IEntry::getTitle));
		similar.getSelectionModel()
		.selectedItemProperty()
		.addListener(similarSelect);
	}
	
	private IEntry result;
	
	@FXML
	private void cancelAction(ActionEvent e) {
		hide();
	}
	@FXML
	private void okAction(ActionEvent e) {
		result = null;
		
		String s = titleTf.getText();
		if(Checker.isEmptyTrimmed(s)){
			FxPopupShop.showHidePopup("Invalid title", 1500);
			return;
		}

		String title = titleTf.getText();
		if(item == null)
			tab.addChild( title);
		else {
			switch (bookMarkType) {
				case RELATIVE:
					result =  tab.addChild(title, item.parent(), item);
					break;
				case CHILD: 
					result =  tab.addChild(title, item);
					break;
				case RELATIVE_TO_PARENT:
					result =  tab.addChild(title,item.parent().parent(), item.parent());
					break;
			}
		}
		super.hide();
	}
	
	@Override
	public void hide() {
		super.hide();
		similar.getItems().clear();
		titleTf.textProperty().removeListener(searcher);
		titleTf.clear();
		search.stop();
	}
	
	public IEntry showDialog(BookmarkType bookMarkType, MultipleSelectionModel<TreeItem<String>> selectionModel, TreeView<String> tree, Tab tab) {
		this.item = (IEntry)selectionModel.getSelectedItem();
		this.tab = tab;

		this.bookMarkType = bookMarkType == RELATIVE_TO_PARENT && item.getParent() == tree.getRoot() ? RELATIVE : bookMarkType;
		header.setText(header());

		fx(() -> titleTf.requestFocus());
		titleTf.clear();
		titleTf.textProperty().addListener(searcher);
		
		Collection<IEntry> list = tab.getAllEntries();
		similar.getItems().setAll(list);
		search.start(list);
		search.setOnChange(() -> fx(()-> search.applyFilter(similar.getItems())));
		
		showAndWait();
		
		if(result != null){
			selectionModel.clearSelection();
			selectionModel.select(result);
		}
		return result;
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
	@Override
	protected void finalize() throws Throwable {
		search.completeStop();
		finalized();
		super.finalize();
	}
}
