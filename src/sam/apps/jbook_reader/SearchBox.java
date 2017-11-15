package sam.apps.jbook_reader;

import static sam.apps.jbook_reader.Utils.button;
import static sam.apps.jbook_reader.Utils.each;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Stage;
import sam.apps.jbook_reader.tabs.Tab;

public final class SearchBox extends Popup {
	private final TextField field = new TextField();
	private final Stage stage;
	private final TreeView<String> bookmarks;
	private final Tab tab;
	private final Button previous, next, clear;
	private final CheckBox inBookmarks = new CheckBox("Bookmarks"); 
	private final CheckBox inContent = new CheckBox("Content");
	private WeakChangeListener<Number> listener;

	private int index = 0;
	private ObservableList<TreeItem<String>> result = FXCollections.observableArrayList();

	public SearchBox(Stage stage, TreeView<String> bookmarks, Tab tab) {
		super();
		
		this.tab = tab;
		this.bookmarks = bookmarks;

		this.stage = stage;
		field.setPrefColumnCount(20);

		HBox box = new HBox(10,
				previous = button("previous", "right-arrow.png", e1 -> move(-1)),
				next = button("next", "right-arrow.png", e1 -> move(+1)),
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
		
		Text status = new Text();
		VBox root = new VBox(5, closeBox, t, field, box, t2, vvb, new Rectangle(2, 5), status);
		
		status.textProperty().bind(Bindings.concat("found: ", Bindings.size(result)));

		root.setId("popup-box");
		root.setOpacity(0.8);
		root.setPadding(new Insets(10));

		getContent().setAll(root);
		setAutoHide(false);
		setHideOnEscape(false);

		field.setOnAction(e -> searchAction());

		show(stage);
		listener = new WeakChangeListener<>((pp, o, n) -> setLocation());
		listener.wasGarbageCollected();
		setLocation();
		
		each(a -> a.addListener(listener), stage.xProperty(), stage.yProperty(), stage.widthProperty(), stage.heightProperty());
		each(a -> a.getStyleClass().add("text"), t, t2, closeButton, inContent, inBookmarks, status);
		each(b -> b.getTooltip().setStyle("-fx-background-color:white;-fx-text-fill:black;"), previous, next, clear, closeButton);

	}
	private void searchAction() {
		String text = field.getText();
		if(text == null || text.isEmpty()) {
			next.setDisable(true);
			previous.setDisable(true);
			clear.setDisable(true);
			index = 0;
			result.clear();
		}
		else {
			result.setAll(tab.search(inBookmarks.isSelected() ? text : null, inContent.isSelected() ? text : null));
			move(0);
		}
	}
	private void clear() {
		field.setText(null);
		field.fireEvent(new ActionEvent());
	}
	private void move(int by) {
		index = index + by;
		bookmarks.getSelectionModel().clearSelection();
		bookmarks.getSelectionModel().select(result.get(index));
		previous.setDisable(index == 0);
		next.setDisable(index == result.size() - 1);
	}
	private void setLocation() {
		setX(stage.getX() + stage.getWidth() - getWidth());
		setY(stage.getY() + 30);	
	}
}
