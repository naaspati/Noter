package sam.apps.jbook_reader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.binding.When;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;

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
		FxFxml.setFxmlDir(ClassLoader.getSystemResource("fxml"));

		this.tab = tab2;
		this.selectionModel = selectionModel;

		searchF.setPrefColumnCount(20);

		HBox box = new HBox(10,
				previous = new Button2("previous", "right-arrow.png", e1 -> previous()),
				next = new Button2("next", "right-arrow.png", e1 -> next()),
				clear = new Button2("clear", "cancel-mark.png", e1 -> clear())
				);
		previous.setRotate(-180);
		Consumer<Button> c = e -> e.opacityProperty().bind(new When(e.disableProperty()).then(0).otherwise(1));
		c.accept(previous);
		c.accept(next);
		c.accept(clear);

		box.setPadding(new Insets(5));
		Text t = new Text("Search");
		Text t2 = new Text("search in: ");

		inBookmarks.setSelected(true);
		VBox vvb = new VBox(3, inBookmarks, inContent);
		vvb.setPadding(new Insets(0, 0, 0, 10));

		Button closeButton = new Button2("close", null, e11 -> {clear.fire(); hide();});
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

		Stage stage = App.getStage(); 
		show(App.getStage());
		listener = new WeakChangeListener<>((pp, o, n) -> setLocation());
		setLocation();

		for(ReadOnlyDoubleProperty a: new ReadOnlyDoubleProperty[] {stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty()} )
			a.addListener(listener);
		
		for (Node b : new Node[] {t, t2, closeButton, inContent, inBookmarks})
			b.getStyleClass().add("text");
		
		for (Button b : new Button[] {previous, next, clear, closeButton})
			b.getTooltip().setStyle("-fx-background-color:white;-fx-text-fill:black;");			
	}
	
	public void start(Tab tab) {
		this.tab = tab;
		clear();
		show(App.getStage());
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
		Stage stage = App.getStage();
		setX(stage.getX() + stage.getWidth() - getWidth());
		setY(stage.getY() + 30);	
	}
}
