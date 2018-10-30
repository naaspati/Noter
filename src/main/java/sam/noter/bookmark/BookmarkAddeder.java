package sam.noter.bookmark;

import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.config.Session;
import sam.logging.InitFinalized;
import sam.myutils.MyUtilsCheck;
import sam.noter.Utils;
import sam.noter.datamaneger.EntryXML;
import sam.noter.tabs.Tab;
import static sam.noter.Utils.castEntry;

class BookmarkAddeder extends Alert implements ChangeListener<String>, InitFinalized  {
	private final TextField tf = new TextField();
	private final TextArea ta = new TextArea();
	private final StringBuilder sb = new StringBuilder();
	
	public BookmarkAddeder() {
		super(AlertType.CONFIRMATION);
		setTitle("Add New Bookmark");
		initOwner(Session.get(Stage.class));

		HBox hb = new HBox(10, new Text("Title "), tf);
		getDialogPane().setContent(hb);   
		hb.setMaxWidth(300);
		HBox.setHgrow(tf, Priority.ALWAYS);
		hb.setAlignment(Pos.CENTER_LEFT);

		getDialogPane().setExpandableContent(new VBox(10, new Text("Similar Bookmarks"), ta));
		getDialogPane().setExpanded(true);
		tf.textProperty().addListener(this);

		init();  
	}

	private Tab tab;

	public void addNewBookmark(BookmarkType bookMarkType, MultipleSelectionModel<TreeItem<String>> selectionModel, TreeView<String> tree, Tab tab) {
		this.tab = tab;
		EntryXML item = (EntryXML)selectionModel.getSelectedItem();

		BookmarkType bt = bookMarkType == RELATIVE_TO_PARENT && item.getParent() == tree.getRoot() ? RELATIVE : bookMarkType;
		setHeaderText(header(item, bt));

		Platform.runLater(() -> tf.requestFocus());
		tf.clear();
		ta.clear();

		showAndWait()
		.map(b -> process(b, bt, item, tree))
		.ifPresent(t -> {
			selectionModel.clearSelection();
			selectionModel.select(t);
		});
	}

	private TreeItem<String> process(ButtonType b, BookmarkType bt, EntryXML item, TreeView<String> tree) {
		if(b != ButtonType.OK) return null;

		String title = tf.getText();
		if(item == null)
			return castEntry(tree.getRoot()).addChild(title, null);
		else {
			switch (bt) {
				case RELATIVE:
					return castEntry(item.getParent()).addChild(title, item);
				case CHILD: 
					return item.addChild(title, null);
				case RELATIVE_TO_PARENT:
					return castEntry(item.getParent().getParent()).addChild(title, (EntryXML)item.getParent());
			}
		}
		return null;

	}
	private String header(EntryXML item, BookmarkType bt) {
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
		return header;
	}

	@Override
	public void changed(ObservableValue<? extends String> observable, String oldValue, String n) {
		if(MyUtilsCheck.isEmpty(n))
			ta.clear();
		else {
			tab.walk()
			.filter(e -> e.getTitle() != null && e.getTitle().contains(n))
			.reduce(sb, (sb2, t) -> Utils.treeToString(t, sb2), StringBuilder::append);
			ta.setText(sb.toString());
			sb.setLength(0);
		}

	}
	
	@Override
	protected void finalize() throws Throwable {
		finalized();
		super.finalize();
	}
}
