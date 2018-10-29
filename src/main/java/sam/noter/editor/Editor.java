package sam.noter.editor;

import static sam.noter.editor.ViewType.CENTER;
import static sam.noter.editor.ViewType.COMBINED_CHILDREN;
import static sam.noter.editor.ViewType.COMBINED_TEXT;
import static sam.noter.editor.ViewType.EXPANDED;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.StringConverter;
import sam.config.Session;
import sam.config.SessionPutGet;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.fxml.Button2;
import sam.logging.MyLoggerFactory;
import sam.noter.datamaneger.Entry;
import sam.reference.WeakAndLazy;

public class Editor extends BorderPane implements SessionPutGet {
	private static final Logger LOGGER = MyLoggerFactory.logger(Editor.class.getSimpleName());

	@FXML private BorderPane editor;
	@FXML private Button2 backBtn;
	@FXML private Label maintitle;
	@FXML private Button2 combineContentBtn;
	@FXML private Button2 combineChildrenBtn;

	private final Consumer<Entry> onExpanded = t -> changed(t, EXPANDED);
	private WeakAndLazy<UnitContainer> unitsContainerWL = new WeakAndLazy<>(() -> new UnitContainer(onExpanded));
	private WeakAndLazy<CombinedText> combinedTextWL = new WeakAndLazy<>(CombinedText::new);
	private final CenterEditor centerEditor = new CenterEditor();
	
	private Entry currentItem;
	private ViewType currentView;

	private final Window parent;
	private final IdentityHashMap<Entry, Stack<ViewType>> history = new IdentityHashMap<>();

	 

	private static Font font;

	public static Font getFont() {
		return font;
	}
	static {
		// Font.font(family, weight, posture, size)
		String family = Session.getProperty(Editor.class, "font.family");
		FontWeight weight = parse("font.weight", FontWeight::valueOf);
		FontPosture posture = parse("font.posture", FontPosture::valueOf);
		Float size = parse("font.size", Float::parseFloat);

		font = Font.font(family, weight, posture, size == null ? -1 : size);
	}
	private static <R> R parse(String key, Function<String, R> parser) {
		try {
			String s = Session.getProperty(Editor.class, key);
			if(s == null)
				return null;
			return parser.apply(s.toUpperCase());
		} catch (Exception e) {
			MyLoggerFactory.logger(Editor.class.getName()).warning("bad "+key+" value: "+Session.getProperty(Editor.class, key));
		}
		return null;
	}

	public Editor(Window parent) throws IOException {
		FxFxml.load(this, true);
		this.parent = parent;
	}
	public void init(ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty){
		Objects.requireNonNull(selectedItemProperty);
		selectedItemProperty.addListener((p, o, n) -> changed((Entry)n, EXPANDED));
		disableProperty().bind(selectedItemProperty.isNull());		
	}
	@FXML
	private void changeEntry(ActionEvent e) {
		ViewType t = e.getSource() == combineContentBtn ? COMBINED_TEXT : COMBINED_CHILDREN;
		changed(centerEditor.getItem(), t);
	}

	@FXML
	private void historyBack(Event e) {
		Stack<ViewType> stack = history.get(currentItem);
		if(stack == null || stack.isEmpty()){
			backBtn.setDisable(true);
			return;
		}
		changed(currentItem, stack.pop());
	}

	public void setWordWrap(boolean wrap) {
		centerEditor.setWordWrap(wrap);
		unitsContainerWL.ifPresent(u -> u.setWordWrap(wrap));
		combinedTextWL.ifPresent(u -> u.setWrapText(wrap));
	}
	public void setFont() {
		Stage stage = new Stage(StageStyle.UTILITY);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(parent);
		stage.setTitle("Select Font");

		GridPane root = new GridPane();
		root.setHgap(5);
		root.setVgap(5);

		// family, weight, posture, size
		root.addRow(0, Stream.of("family:", "weight:", "posture:", "size:").map(Text::new).toArray(Text[]::new));

		ComboBox<String> family = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
		ComboBox<FontWeight> weight = new ComboBox<>(FXCollections.observableArrayList(FontWeight.values()));
		ComboBox<FontPosture> posture = new ComboBox<>(FXCollections.observableArrayList(FontPosture.values()));
		ComboBox<Double> size = new ComboBox<>();
		IntStream.rangeClosed(8, 12).mapToDouble(s -> s).forEach(size.getItems()::add);
		IntStream.iterate(14, i -> i + 2).limit(8).mapToDouble(s -> s).forEach(size.getItems()::add);
		size.getItems().addAll(36d, 48d, 72d);
		size.setEditable(true);

		Font font = Optional.of(Editor.font).orElse(Font.font("Consolas"));

		family.setValue(font.getFamily());
		weight.setValue(FontWeight.NORMAL);
		posture.setValue(FontPosture.REGULAR);
		size.setValue(font.getSize());
		size.setMaxWidth(70);
		family.setMaxWidth(150);
		posture.setMaxWidth(100);
		weight.setMaxWidth(100);

		Double[] size2 = {font.getSize()};
		size.setConverter(new StringConverter<Double>() {
			@Override
			public String toString(Double object) {
				size2[0] = object;
				String s = String.valueOf(object);
				return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
			}

			@Override
			public Double fromString(String string) {
				try {
					Double.parseDouble(string);
				} catch (NumberFormatException e) {}

				return size2[0];
			}
		});

		root.addRow(1, family, weight, posture, size);

		TextArea ta = new TextArea(IntStream.range(0, 10).mapToObj(String::valueOf).collect(Collectors.joining("", "The quick brown fox jumps over the lazy dog ", "")));
		ta.setWrapText(true);
		ta.fontProperty().bind(Bindings.createObjectBinding(() -> Font.font(family.getValue(), weight.getValue(), posture.getValue(), size.getValue()), family.valueProperty(), weight.valueProperty(), posture.valueProperty(), size.valueProperty()));

		root.addRow(2, new Text("Dummy text"));
		root.add(ta, 0, 3, GridPane.REMAINING, GridPane.REMAINING);

		BorderPane root1 = new BorderPane(root);
		Button ok = new Button("OK");
		Button cancel = new Button("Cancel");
		ok.setPrefWidth(70);
		cancel.setPrefWidth(70);
		cancel.setOnAction(e -> stage.hide());
		HBox bottom = new HBox(10, cancel, ok);
		bottom.setAlignment(Pos.CENTER_RIGHT);
		bottom.setPadding(new Insets(10));
		root1.setBottom(bottom);

		root1.setPadding(new Insets(10, 10, 0, 10));
		root1.setStyle("-fx-background-color:white");

		stage.setScene(new Scene(root1));
		stage.setWidth(470);

		ok.setOnAction(e -> {
			Editor.font = ta.getFont(); 
			stage.hide();
			centerEditor.updateFont();
			unitsContainerWL.ifPresent(UnitContainer::updateFont);
			combinedTextWL.ifPresent(t -> t.setFont(font));

			sessionPut("font.family", family.getValue());
			sessionPut("font.weight",weight.getValue().toString());
			sessionPut("font.posture",posture.getValue().toString());
			sessionPut("font.size", size.getValue().toString());
		});
		stage.showAndWait();
	}

	public void consume(Consumer<TextArea> e) {
		if(getCenter() != centerEditor)
			FxPopupShop.showHidePopup("no text selected", 1500);
		else {

			centerEditor.consume(e);
		}
	}
	public void changed(Entry item, ViewType view) {
		if(view == null) view = CENTER;

		if(item == null) {
			unitsContainerWL.ifPresent(UnitContainer::clear);
			maintitle.setText(null);
			super.setCenter(null);
			backBtn.setVisible(false);
			combineChildrenBtn.setVisible(false);
			combineContentBtn.setVisible(false);
			return;
		}

		switch (view) {
			case EXPANDED:
				setCenterEditor(item);
				history.remove(item);
				break;
			case CENTER:
				setCenterEditor(item);
				addHistory(item);
				break;
			case COMBINED_CHILDREN:
				setCombined_children(item);
				break;
			case COMBINED_TEXT:
				setCombined_text(item);
				break;
		}
		
		currentItem = item;
		currentView = view;
	}
	private void setCombined_text(Entry item) {
		CombinedText c = combinedTextWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(item);
		maintitle.setText(null);

		combineChildrenBtn.setVisible(true);
		combineContentBtn.setVisible(false);
	}
	private void setCombined_children(Entry item) {
		UnitContainer c = unitsContainerWL.get();
		if(getCenter() == c && c.getItem() == item) return;

		if(getCenter() != c)
			setCenter(c);
		c.setItem(item);
		addHistory(item);
		maintitle.setText(item.getTitle());

		combineChildrenBtn.setVisible(false);
		combineContentBtn.setVisible(true);
	}

	private void addHistory(Entry item) {
		ViewType type = currentView;
		if(type == null) return;
		Stack<ViewType> stack = history.computeIfAbsent(item, v -> new Stack<>());
		if(stack.isEmpty() || stack.lastElement() != type){
			stack.add(type);
			LOGGER.fine(() -> "ADD TO HISTORY: "+type+":  "+item.getTitle());
		}
		backBtn.setVisible(!stack.isEmpty());
	}
	private void setCenterEditor(Entry item) {
		if(getCenter() != centerEditor)
			setCenter(centerEditor);

		centerEditor.setItem(item);

		unitsContainerWL.ifPresent(UnitContainer::clear);
		combinedTextWL.ifPresent(CombinedText::clear);
	}
	public void updateTitle(Entry ti) {
		unitsContainerWL.ifPresent(u -> u.updateTitle(ti));

		centerEditor.updateTitle();
		if(getCenter() == centerEditor)
			maintitle.setText(centerEditor.getItemTitle());
		else {
			UnitContainer u = unitsContainerWL.peek();

			if(u == null || u.isEmpty())
				maintitle.setText(null);
			else
				maintitle.setText(u.first().getItemTitle());
		}
	}
	public void close() {
		centerEditor.close();
	}

}
