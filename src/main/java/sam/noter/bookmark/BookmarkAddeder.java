package sam.noter.bookmark;

import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.fx.popup.FxPopupShop;
import sam.myutils.MyUtilsCheck;
import sam.noter.InitFinalized;
import sam.noter.dao.Entry;
import sam.noter.tabs.Tab;

class BookmarkAddeder extends Stage implements InitFinalized, ChangeListener<String> {
	private final TextField titleTf = new TextField();
	private final ListView<Entry> similar = new ListView<>();
	private final Label header = new Label();
	private final Label entryPath = new Label(); 
	private final TitleSearch search = new TitleSearch();
	private Tab tab;
	private final WeakChangeListener<Entry> similarSelect = new WeakChangeListener<>((p, o, n) -> entryPath.setText(n == null ? null : n.toTreeString()));
	private MultipleSelectionModel<TreeItem<String>> selectionModel;
	private BookmarkType bookMarkType;
	private Entry item;

	public BookmarkAddeder() {
		super(StageStyle.UTILITY);
		setTitle("Add New Bookmark");
		header.setStyle("-fx-padding:10;-fx-font-family:'Consolas';-fx-font-size:1.3em;-fx-background-color:white");
		header.setWrapText(true);
		
		Text t = new Text("Title");
		t.setTextAlignment(TextAlignment.CENTER);
		HBox box = new HBox(10, t, titleTf);
		HBox.setHgrow(titleTf, Priority.ALWAYS);
		box.setAlignment(Pos.CENTER);
		titleTf.setMaxWidth(Double.MAX_VALUE);
		
		VBox center = new VBox(10, box, new Text("Similar Bookmarks"), similar, entryPath);
		center.setStyle("-fx-padding:10;-fx-border-width:1 0 0 0;-fx-border-color:gray;-fx-font-family:'Consolas';-fx-font-size:1.1em; ");
		
		Button ok = new Button("ADD");
		Button cancel = new Button("CANCEL");
		ok.setDefaultButton(true);
		cancel.setCancelButton(true);
		ok.setOnAction(this::okAction);
		cancel.setOnAction(e -> hide());
		
		HBox bottom = new HBox(10, ok, cancel);
		bottom.setPadding(new Insets(0, 10, 10, 10));
		bottom.setAlignment(Pos.CENTER_RIGHT);

		similar.setPlaceholder(new Text("NOTHING"));
		similar.getSelectionModel()
		.selectedItemProperty()
		.addListener(similarSelect);
		
		setScene(new Scene(new BorderPane(center, header, null, bottom, null)));
		setWidth(300);
		setHeight(400);
		init();  
	}
	
	private Entry result;
	
	private void okAction(ActionEvent e) {
		result = null;
		
		String s = titleTf.getText();
		if(MyUtilsCheck.isEmptyTrimmed(s)){
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
		titleTf.textProperty().removeListener(this);
		titleTf.clear();
		search.stop();
	}
	
	public Entry showDialog(BookmarkType bookMarkType, MultipleSelectionModel<TreeItem<String>> selectionModel, TreeView<String> tree, Tab tab) {
		this.selectionModel = selectionModel;
		this.item = (Entry)selectionModel.getSelectedItem();
		this.tab = tab;

		this.bookMarkType = bookMarkType == RELATIVE_TO_PARENT && item.getParent() == tree.getRoot() ? RELATIVE : bookMarkType;
		header.setText(header());

		Platform.runLater(() -> titleTf.requestFocus());
		titleTf.clear();
		titleTf.textProperty().addListener(this);
		
		search.start(tab);
		search.setOnChange(() -> Platform.runLater(()-> search.process(similar.getItems())));
		
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
	public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
		search.search(newValue == null ? newValue : newValue.toLowerCase());
	}

	@Override
	protected void finalize() throws Throwable {
		search.completeStop();
		finalized();
		super.finalize();
	}
}
