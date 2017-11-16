package sam.apps.jbook_reader.editor;

import static sam.apps.jbook_reader.Utils.addClass;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import sam.apps.jbook_reader.Utils;
import sam.apps.jbook_reader.Viewer;
import sam.apps.jbook_reader.tabs.Tab;
import sam.properties.session.Session;

public class Editor extends BorderPane {
	private final VBox container = new VBox(15);
	private final ScrollPane containerScrollPane = new ScrollPane(container);
	private final Label maintitle = new Label();
	private final ReadOnlyObjectProperty<Tab> currentTabProperty;
	private UnitEditor activeEditor;
	private Consumer<UnitEditor> onUnitEditorSelected = this::onUnitEditorSelected;
	
	private static transient Editor instance;
	private static Font font;
	
	public static Font getFont() {
		return font;
	}

	public static Editor getInstance(ReadOnlyObjectProperty<Tab> currentTabProperty, ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty) {
		if (instance == null) {
			synchronized (Editor.class) {
				if (instance == null)
					instance = new Editor(currentTabProperty, selectedItemProperty);
			}
		}
		return instance;
	}
	private Editor(ReadOnlyObjectProperty<Tab> currentTabProperty, ReadOnlyObjectProperty<TreeItem<String>> selectedItemProperty) {
		Objects.requireNonNull(currentTabProperty);
		Objects.requireNonNull(selectedItemProperty);

		this.currentTabProperty = currentTabProperty;
		
		loadFont();

		currentTabProperty.addListener((p, o, n) -> tabChange(n));
		selectedItemProperty.addListener((p, o, n) -> itemChange(n));

		disableProperty().bind(selectedItemProperty.isNull());

		setId("editor");
		addClass(maintitle,"main-title");
		addClass(container,"container");
		
		container.setPadding(new Insets(10, 0, 10, 0));

		maintitle.setMaxWidth(Double.MAX_VALUE);
		maintitle.setWrapText(true);

		setTop(maintitle);
	}
	private void loadFont() {
		// Font.font(family, weight, posture, size)
		String family = Session.get("editor.font.family");
		FontWeight weight = parse("editor.font.weight", FontWeight::valueOf);
		FontPosture posture = parse("editor.font.posture", FontPosture::valueOf);
		Float size = parse("editor.font.size", Float::parseFloat);
		
		font = Font.font(family, weight, posture, size == null ? -1 : size);
	}

	private <R> R parse(String key, Function<String, R> parser) {
		try {
			String s = Session.get(key);
			if(s == null)
				return null;
			return parser.apply(s.toUpperCase());
		} catch (Exception e) {
			Logger.getLogger(Editor.class.getName()).warning("bad "+key+" value: "+Session.get(key));
		}
		return null;
	}

	public void finish(Tab tab) {
		if(currentTabProperty.get() == tab)
			finishUnitEditors();	
	}
	private void finishUnitEditors() {
		if(getCenter() != null && getCenter() instanceof UnitEditor)
			((UnitEditor)getCenter()).finish();
		
		container.getChildren().stream()
		.map(UnitEditor.class::cast)
		.forEach(UnitEditor::finish);
	}
	private void itemChange(TreeItem<String> nnew) {
		if(activeEditor != null && activeEditor.getItem() == nnew)
			return;

		finishUnitEditors();
		container.getChildren().clear();
		maintitle.setText(null);
		containerScrollPane.setFitToWidth(true);

		if(nnew == null)
			return;

		if(nnew.getChildren().isEmpty()) {
			activeEditor = newUnitEditor(nnew);
			maintitle.setText(activeEditor.getItemTitle());
			setCenter(activeEditor);
			return;
		}

		 container.getChildren().add(newUnitEditor(nnew));

		// currentTabProperty.get().walk(nnew)
		 nnew.getChildren().stream()
		.map(this::newUnitEditor)
		.forEach(container.getChildren()::add);

		Optional<UnitEditor> ue = container.getChildren().stream()
				.map(UnitEditor.class::cast)
				.filter(UnitEditor::isActive)
				.findFirst();

		activeEditor = ue.isPresent() ? ue.get() : null;

		setCenter(null);
		setCenter(activeEditor == null ? containerScrollPane : activeEditor);
		maintitle.setText(activeEditor == null ? currentTabProperty.get().getTitle(nnew) :  activeEditor.getItemTitle());
	}
	WeakHashMap<TreeItem<String>, WeakReference<UnitEditor>> editorsMap = new WeakHashMap<>();
			
	private UnitEditor newUnitEditor(TreeItem<String> nnew) {
		WeakReference<UnitEditor> wr =  editorsMap.get(nnew);
		if(wr == null) 
			return putEditor(nnew);
		
		UnitEditor ue = wr.get();
		if(ue == null) {
			System.out.println("gc");
			return putEditor(nnew);
		}
		return ue;
	}
	private UnitEditor putEditor(TreeItem<String> nnew) {
		UnitEditor ue = new UnitEditor(currentTabProperty.get(), nnew, onUnitEditorSelected);
		editorsMap.put(nnew, new WeakReference<UnitEditor>(ue));
		return ue;
	}
	private void tabChange(Tab nnew) {
		finishUnitEditors();
		container.getChildren().clear();
		maintitle.setText(null);
		setCenter(null);
	}

	private void onUnitEditorSelected(UnitEditor ue) {
		if(activeEditor != null)
			activeEditor.setActive(false);

		activeEditor = ue;
		activeEditor.setActive(true);
		setCenter(activeEditor);
		maintitle.setText(activeEditor.getItemTitle());
	}
	
	private Stream<UnitEditor> unitEditors() {
		return editorsMap.values().stream()
		.map(WeakReference::get)
		.filter(Objects::nonNull);
	}

	public void setWordWrap(boolean wrap) {
		unitEditors().forEach(u -> u.setWordWrap(wrap));
	}
	private void updateFont() {
		unitEditors().forEach(UnitEditor::updateFont);
	}
	public void setFont() {
		Stage stage = new Stage(StageStyle.UTILITY);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.initOwner(Viewer.getStage());
		stage.setTitle("Select Font");
		
		GridPane root = new GridPane();
		root.setHgap(5);
		root.setVgap(5);

		// family, weight, posture, size
		root.addRow(0, Stream.of("family:", "weight:", "posture:", "size:").map(Utils::text).toArray(Text[]::new));

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
			updateFont();
			
			Session.put("editor.font.family", family.getValue());
			Session.put("editor.font.weight",weight.getValue().toString());
			Session.put("editor.font.posture",posture.getValue().toString());
			Session.put("editor.font.size", size.getValue().toString());
		});
		stage.showAndWait();
	}

}
