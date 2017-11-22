package sam.apps.jbook_reader.editor;

import static sam.apps.jbook_reader.Utils.addClass;
import static sam.apps.jbook_reader.Utils.button;

import java.lang.ref.WeakReference;
import java.util.Arrays;
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
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import javafx.scene.layout.Priority;
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
	private UnitEditor currentEditor;
	private final Button backBtn, combineBtn;
	private final SimpleObjectProperty<Node> backToContent = new SimpleObjectProperty<>();  
	private final Consumer<UnitEditor> onEditStarted = this::onEditStarted;
	private boolean wrapText;
	private TextArea combinedTextArea;

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

		maintitle.setPadding(new Insets(10));
		maintitle.setMaxWidth(Double.MAX_VALUE);
		maintitle.setWrapText(true);

		HBox hb = new HBox( 
				backBtn = button("back", "Back_30px.png", e -> backToContentAction()),
				maintitle,
				combineBtn = button("combine children", "Plus Math_30px.png", e -> combineAction()));

		setTop(hb);
		hb.setAlignment(Pos.CENTER);
		HBox.setHgrow(maintitle, Priority.ALWAYS);

		backBtn.visibleProperty().bind(backToContent.isNotNull());
		combineBtn.visibleProperty().bind(centerProperty().isEqualTo(containerScrollPane).and(Bindings.size(container.getChildren()).greaterThan(1)));
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
		backToContent.set(null);
		
		if(currentEditor != null && currentEditor.getItem() == nnew)
			return;

		finishUnitEditors();
		container.getChildren().clear();
		maintitle.setText(null);
		containerScrollPane.setFitToWidth(true);

		if(nnew == null)
			return;

		currentEditor = newUnitEditor(nnew);

		if(nnew.getChildren().isEmpty() || currentEditor.isActive()) {
			maintitle.setText(currentEditor.getItemTitle());
			setCenter(currentEditor);
			return;
		}

		currentEditor = null;

		container.getChildren().add(newUnitEditor(nnew));
		nnew.getChildren().stream()
		.map(this::newUnitEditor)
		.forEach(container.getChildren()::add);

		setCenter(null);
		setCenter(currentEditor == null ? containerScrollPane : currentEditor);
		containerScrollPane.setVvalue(0);
		maintitle.setText(currentEditor == null ? currentTabProperty.get().getTitle(nnew) :  currentEditor.getItemTitle());
		
		if(combinedTextArea != null && nnew == combinedTextArea.getUserData())
			combineAction();
	}
	WeakHashMap<TreeItem<String>, WeakReference<UnitEditor>> editorsMap = new WeakHashMap<>();

	private UnitEditor newUnitEditor(TreeItem<String> nnew) {
		return 
				Optional.ofNullable(editorsMap.get(nnew))
				.map(WeakReference::get)
				.orElseGet(() -> putEditor(nnew));
	}
	private UnitEditor putEditor(TreeItem<String> nnew) {
		UnitEditor ue = new UnitEditor(currentTabProperty.get(), nnew, onEditStarted);
		ue.setWordWrap(wrapText);
		editorsMap.put(nnew, new WeakReference<UnitEditor>(ue));
		return ue;
	}
	private void backToContentAction() {
		if(combinedTextArea != null) {
			combinedTextArea.setText(null);
			combinedTextArea.setUserData(null);
		}
		setCenter(backToContent.get());
		backToContent.set(null);
	}
	private void tabChange(Tab nnew) {
		finishUnitEditors();
		container.getChildren().clear();
		maintitle.setText(null);
		setCenter(null);
	}
	public void updateTitle() {
		unitEditors().forEach(UnitEditor::updateTitle);
		if(currentEditor != null)
			maintitle.setText(currentEditor.getItemTitle());
	}
	private void combineAction() {
		String content = container.getChildren().stream().map(UnitEditor.class::cast).reduce(new StringBuilder(), (sb, u) -> {
			char[] chars = new char[u.getItemTitle().length() + 10];
			Arrays.fill(chars, '#');
			sb.append(chars).append('\n')
			.append("     ").append(u.getItemTitle()).append('\n')
			.append(chars).append('\n').append('\n')
			.append(u.getContent()).append('\n').append('\n');
			return sb;
		}, StringBuilder::append).toString();

		backToContent.set(containerScrollPane);
		combinedTextArea = combinedTextArea != null ? combinedTextArea : new TextArea();
		combinedTextArea.setText(content);
		combinedTextArea.setUserData(((UnitEditor)container.getChildren().get(0)).getItem());
		combinedTextArea.setEditable(false);
		combinedTextArea.setWrapText(wrapText);
		setCenter(combinedTextArea);

	}	
	private void onEditStarted(UnitEditor ue) {
		if(currentEditor != null)
			currentEditor.setActive(false);

		currentEditor = ue;
		currentEditor.setActive(true);
		if(getCenter() == containerScrollPane)
			backToContent.set(containerScrollPane);
		
		setCenter(currentEditor);
		maintitle.setText(currentEditor.getItemTitle());
	}

	private Stream<UnitEditor> unitEditors() {
		return editorsMap.values().stream()
				.map(WeakReference::get)
				.filter(Objects::nonNull);
	}

	public void setWordWrap(boolean wrap) {
		wrapText = wrap;
		if(combinedTextArea != null)
			combinedTextArea.setWrapText(wrap);
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
