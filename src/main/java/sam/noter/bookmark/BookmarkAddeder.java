package sam.noter.bookmark;

import static sam.fx.helpers.FxClassHelper.addClass;
import static sam.noter.Utils.fx;
import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;
import static sam.noter.EntryTreeItem.cast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

import javax.inject.Singleton;

import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import sam.fx.helpers.FxButton;
import sam.fx.helpers.FxCell;
import sam.fx.helpers.FxHBox;
import sam.fx.popup.FxPopupShop;
import sam.fx.textsearch.FxTextSearch;
import sam.myutils.Checker;
import sam.nopkg.EnsureSingleton;
import sam.noter.EntryTreeItem;
import sam.noter.Utils;
import sam.noter.app.AppUtils;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;

@Singleton
class BookmarkAddeder extends BorderPane {
	private static final EnsureSingleton singleton = new EnsureSingleton();

	private final Label header = new Label();
	private final TextField searchTF = new TextField();
	private final ListView<Wrap> similar = new ListView<>();
	private final TextArea entryPath = new TextArea();
	
	private final AppUtils utils;
	private final ArrayList<Wrap> allData = new ArrayList<>();
	
	private Runnable close;
	private BookmarkType bookMarkType;
	private EntryTreeItem item;
	private BookMarkTree root;
	private int mod = -100;
	private WeakReference<IRootEntry> wroot = new WeakReference<>(null);
	private final FxTextSearch<Wrap> search = new FxTextSearch<>(w -> w.string, 500, true);

	public BookmarkAddeder(AppUtils utils) throws IOException {
		singleton.init();
		this.utils = utils;
		
		setTop(header);
		setCenter(new VBox(5, similar, entryPath));
		
		header.setWrapText(true);
		entryPath.setPrefRowCount(3);
		entryPath.setWrapText(true);
		
		addClass(header, "header");
		addClass(similar, "similar");
		addClass(entryPath, "entryPath");
		
		setBottom(FxHBox.buttonBox(
				FxButton.button("OK", this::okAction),
				FxButton.button("CANCEL", e -> close())
				));
		
		search.disable();
		search.set(w -> w != null && w.string != null);
		search.setOnChange(() -> search.applyFilter(similar.getItems()));
		similar.setCellFactory(FxCell.listCell(w -> w == null || w.entry == null ? null : w.entry.getTitle()));
		
		StringBuilder sb = new StringBuilder();
		
		similar.getSelectionModel()
		.selectedItemProperty()
		.addListener((p, o, n) -> {
			if(n == null || n.entry == null) 
				entryPath.setText(null);
			else {
				sb.setLength(0);
				Utils.toTreeString(n.entry, sb);
				entryPath.setText(n == null ? null : sb.toString());	
			} 
		});
	}

	private void okAction(ActionEvent e) {
		EntryTreeItem result = null;

		String s = searchTF.getText();
		if(Checker.isEmptyTrimmed(s)){
			FxPopupShop.showHidePopup("Invalid title", 1500);
			return;
		}

		String title = searchTF.getText();
		if(item == null)
			root.addChild(title, cast(root.getRoot()), Integer.MAX_VALUE);
		else {
			EntryTreeItem parent;
			switch (bookMarkType) {
				case RELATIVE:
					parent = cast(item.getParent()); 
					result =  root.addChild(title, parent, parent.indexOf(item));
					break;
				case CHILD: 
					result =  root.addChild(title, item, Integer.MAX_VALUE);
					break;
				case RELATIVE_TO_PARENT:
					parent = (EntryTreeItem)item.getParent().getParent();
					result =  root.addChild(title, parent, parent.indexOf((EntryTreeItem) item.getParent()));
					break;
			}
		}

		if(result != null) 
			root.clearAndSelect(result);
		
		close();
	}
	
	private void close() {
		search.disable();
		searchTF.clear();
		close.run();
		close = null;
	}

	private static class Wrap {
		public IEntry entry;
		public String string;
		
		public Wrap(IEntry entry) {
			set(entry);
		}
		public void set(IEntry entry) {
			this.entry = entry;
			this.string = entry == null ? null : entry.getTitle().toLowerCase();
		}
	}

	private int index;
	public void showDialog(BookmarkType bookMarkType, BookMarkTree tree) {
		this.root = tree;
		search.disable();

		this.bookMarkType = bookMarkType == RELATIVE_TO_PARENT && item.getParent() == tree.getRoot() ? RELATIVE : bookMarkType;
		header.setText(header());

		fx(() -> searchTF.requestFocus());
		searchTF.clear();
		
		this.item = tree.getSelectedItem();
		IRootEntry root = tree.getRootEntry();
		if(root != this.wroot.get() || mod != root.modCount()) {
			this.wroot = new WeakReference<IRootEntry>(root);
			this.mod = root.modCount();
			
			index = 0;
			root.forEachFlattened(e -> {
				int n = index++;
				if(n >= allData.size())
					allData.add(new Wrap(e));
				else 
					allData.get(n).set(e);
			});
			
			for (int i = index; i < allData.size(); i++)
				allData.get(i).set(null);
		}
		
		similar.getItems().setAll(allData);
		search.setAllData(allData);
		search.enable();
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
