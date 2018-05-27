package sam.apps.jbook_reader;

import static sam.apps.jbook_reader.Utils.each;
import static sam.fx.helpers.FxHelpers.button;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javafx.beans.binding.When;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Stage;
import sam.apps.jbook_reader.datamaneger.Entry;
import sam.apps.jbook_reader.tabs.Tab;
import sam.fx.popup.FxPopupShop;

public final class SearchBox extends Popup {
	private final TextField searchF = new TextField();
	private final MultipleSelectionModel<TreeItem<String>> selectionModel;
	private Tab tab;
	private final Button previous, next, clear;
	private final CheckBox inBookmarks = new CheckBox("Bookmarks"); 
	private final CheckBox inContent = new CheckBox("Content");
	private WeakChangeListener<Number> listener;

	private int index = 0;
	private List<Entry> result = new ArrayList<>();
	private Iterator<Entry> iterator;

	public SearchBox(MultipleSelectionModel<TreeItem<String>> selectionModel, Tab tab2) {
		super();

		this.tab = tab2;
		this.selectionModel = selectionModel;

		searchF.setPrefColumnCount(20);

		HBox box = new HBox(10,
				previous = button("previous", "right-arrow.png", e1 -> previous()),
				next = button("next", "right-arrow.png", e1 -> next()),
				clear = button("clear", "cancel-mark.png", e1 -> clear())
				);
		previous.setRotate(-180);
		each(e -> e.opacityProperty().bind(new When(e.disableProperty()).then(0).otherwise(1)), previous, next, clear);

		box.setPadding(new Insets(5));
		Text t = new Text("Search");
		Text t2 = new Text("search in: ");

		inBookmarks.setSelected(true);
		VBox vvb = new VBox(3, inBookmarks, inContent);
		vvb.setPadding(new Insets(0, 0, 0, 10));

		Button closeButton = button("close", null, e11 -> {clear.fire(); hide();});
		closeButton.setText("x");
		closeButton.setPrefWidth(10);

		HBox closeBox = new HBox(closeButton);
		closeBox.setAlignment(Pos.TOP_RIGHT);

		VBox root = new VBox(5, closeBox, t, searchF, box, t2, vvb);

		root.setId("popup-box");
		root.setOpacity(0.8);
		root.setPadding(new Insets(10));

		getContent().setAll(root);
		setAutoHide(false);
		setHideOnEscape(false);

		searchF.setOnAction(e -> searchAction());

		Stage stage = Viewer.getStage(); 
		show(Viewer.getStage());
		listener = new WeakChangeListener<>((pp, o, n) -> setLocation());
		setLocation();

		each(a -> a.addListener(listener), stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty());
		each(a -> a.getStyleClass().add("text"), t, t2, closeButton, inContent, inBookmarks);
		each(b -> b.getTooltip().setStyle("-fx-background-color:white;-fx-text-fill:black;"), previous, next, clear, closeButton);
	}
	public void start(Tab tab) {
		this.tab = tab;
		clear();
		show(Viewer.getStage());
	}
	private void searchAction() {
		String text = searchF.getText();
		index = 0;
		result.clear();
		
		if(text == null || text.isEmpty()) {
			next.setDisable(true);
			previous.setDisable(true);
			clear.setDisable(true);
			iterator = null;
		}
		else {
			iterator = tab.walk().iterator();
			next();
		}
	}
	private void clear() {
		searchF.setText(null);
		searchF.fireEvent(new ActionEvent());
	}
	private void next() {
		if(index < result.size() - 1)
			select(++index);
		else {
			Entry e = nextInIterator();
			if(e == null)
				FxPopupShop.showHidePopup("nothing found", 2000);
			else
				select(index);
		}
		updateDisable();
	}
	private void updateDisable() {
		previous.setDisable(index <= 0);
		next.setDisable(iterator == null && index == result.size() - 1);
	}
	private void previous() {
		select(--index);
		updateDisable();
	}
	private Entry nextInIterator() {
		boolean inB = inBookmarks.isSelected(), inC = inContent.isSelected();
		String text = searchF.getText();

		while(iterator.hasNext()) {
			Entry e = iterator.next();
			if(
					(inB && e.getTitle() != null && e.getTitle().contains(text)) ||
					(inC && e.getContent() != null && e.getContent().contains(text))
					) {
				result.add(e);
				index = result.size() - 1;
				return e;
			}
		}
		iterator = null;
		return null;
	}
	private void select(int index) {
		selectionModel.clearSelection();
		selectionModel.select(result.get(index));
	}
	private void setLocation() {
		Stage stage = Viewer.getStage();
		setX(stage.getX() + stage.getWidth() - getWidth());
		setY(stage.getY() + 30);	
	}
}
