package sam.apps.jbook_reader.editor;

import static sam.fx.helpers.FxHelpers.addClass;
import static sam.fx.helpers.FxHelpers.button;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import sam.apps.jbook_reader.Viewer;
import sam.apps.jbook_reader.datamaneger.Entry;
import sam.apps.jbook_reader.tabs.Tab;
import sam.fx.helpers.FxHelpers;
import sam.properties.session.Session;

public class Editor extends BorderPane {
	private final VBox container = new VBox(15);
	private final ScrollPane containerScrollPane = new ScrollPane(container);
	private final Label maintitle = new Label();
	private final Button backBtn, combineBtn;
	private final SimpleObjectProperty<Node> backToContent = new SimpleObjectProperty<>();  
	private final Consumer<Entry> onExpanded = this::onExpanded;
	private final CenterEditor centerEditor = new CenterEditor();
	private boolean wrapText;
	private TextArea combinedTextArea;
	private final LinkedList<TreeItem<String>> expandedItems = new LinkedList<>();

	private static volatile Editor instance;
	private static Font font;

	public static Font getFont() {
		return font;
	}
	static {
		// Font.font(family, weight, posture, size)
		String family = Session.get("editor.font.family");
		FontWeight weight = parse("editor.font.weight", FontWeight::valueOf);
		FontPosture posture = parse("editor.font.posture", FontPosture::valueOf);
		Float size = parse("editor.font.size", Float::parseFloat);

		font = Font.font(family, weight, posture, size == null ? -1 : size);
	}
	private static <R> R parse(String key, Function<String, R> parser) {
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

		containerScrollPane.setFitToWidth(true);

		currentTabProperty.addListener(e -> tabChange());
		selectedItemProperty.addListener((p, o, n) -> itemChange((Entry)n));

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
	private void itemChange(Entry nnew) {
		backToContent.set(null);

		if(centerEditor.getItem() == nnew) {
			setCenter(null);
			setCenter(centerEditor);
			return;
		}
		if(nnew == null) {
			resizeContainer(0);
			maintitle.setText(null);
			setCenter(null);
			return;
		}
		if(nnew.getChildren().isEmpty() || expandedItems.contains(nnew)) {
			centerEditor.setItem(nnew);
			maintitle.setText(centerEditor.getItemTitle());
			setCenter(null);
			setCenter(centerEditor);
			return;
		}
		resizeContainer(nnew.getChildren().size() + 1);
		getUnitEditorAt(0).setItem(nnew);
		int index = 1;
		for (TreeItem<String> ti : nnew.getChildren()) getUnitEditorAt(index++).setItem((Entry)ti); 

		setCenter(null);
		setCenter(containerScrollPane);
		containerScrollPane.setVvalue(0);
		maintitle.setText(nnew.getTitle());

		if(combinedTextArea != null && nnew == combinedTextArea.getUserData())
			combineAction();
	}
	private UnitEditor getUnitEditorAt(int index) {
		return (UnitEditor)container.getChildren().get(index);
	}
	private void backToContentAction() {
		if(combinedTextArea != null) {
			combinedTextArea.setText(null);
			combinedTextArea.setUserData(null);
		}
		setCenter(backToContent.get());
		backToContent.set(null);
	}
	private void tabChange() {
		resizeContainer(0);
		maintitle.setText(null);
		centerEditor.clear();
		setCenter(null);
	}

	private final LinkedList<WeakReference<UnitEditor>> unitEditors = new LinkedList<>();
	private void resizeContainer(int newSize) {
		List<Node> list = container.getChildren();

		if(container.getChildren().size() > newSize) {
			list = list.subList(newSize, list.size());
			list.forEach(n -> unitEditors.add(new WeakReference<UnitEditor>((UnitEditor)n)));
			list.clear();
		}
		else {
			while(list.size() < newSize) {
				if(unitEditors.isEmpty())
					list.add(new UnitEditor(onExpanded));
				else {
					Node n = unitEditors.pop().get();
					if(n != null) list.add(n);
				}
			}
			for (Node n : list) {
				UnitEditor e = (UnitEditor)n;
				e.setWordWrap(wrapText);
			}
		}
	}
	public void updateTitle(Entry item) {
		containerChildren().forEach(UnitEditor::updateTitle);
		unitEditors.stream().map(WeakReference::get).filter(Objects::nonNull).forEach(UnitEditor::updateTitle);
		centerEditor.updateTitle();
		if(getCenter() == centerEditor)
			maintitle.setText(centerEditor.getItemTitle());
		else if(container.getChildren().isEmpty())
			maintitle.setText(null);
		else
			maintitle.setText(getUnitEditorAt(0).getItemTitle());
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
	private void onExpanded(Entry item) {
		centerEditor.setItem(item);
		if(getCenter() == containerScrollPane)
			backToContent.set(containerScrollPane);

		setCenter(centerEditor);
		maintitle.setText(centerEditor.getItemTitle());
	}

	private Stream<UnitEditor> containerChildren() {
		return container.getChildren().stream()
				.map(n -> (UnitEditor)n);
	}
	public void setWordWrap(boolean wrap) {
		wrapText = wrap;
		if(combinedTextArea != null)
			combinedTextArea.setWrapText(wrap);
		containerChildren().forEach(u -> u.setWordWrap(wrap));
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
		root.addRow(0, Stream.of("family:", "weight:", "posture:", "size:").map(FxHelpers::text).toArray(Text[]::new));

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
			unitEditors.stream().map(WeakReference::get).filter(Objects::nonNull).forEach(UnitEditor::updateFont);
			containerChildren().forEach(UnitEditor::updateFont);

			Session.put("editor.font.family", family.getValue());
			Session.put("editor.font.weight",weight.getValue().toString());
			Session.put("editor.font.posture",posture.getValue().toString());
			Session.put("editor.font.size", size.getValue().toString());
		});
		stage.showAndWait();
	}
}
