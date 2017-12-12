package sam.apps.jbook_reader.editor;

import static sam.fx.helpers.FxHelpers.addClass;
import static sam.fx.helpers.FxHelpers.button;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import sam.weakstore.WeakStore;

public class Editor extends BorderPane {
	private enum View {
		CENTER, 
		COMBINED_TEXT,
		COMBINED_CHILDREN, 
		EXPANDED
	}

	private VBox container;
	private ScrollPane containerScrollPane;
	private final Label maintitle = new Label();
	private final Button backBtn, combineContentBtn, combineChildrenBtn;
	private final Consumer<Entry> onExpanded = t -> changeEntry(t, View.EXPANDED);
	private final CenterEditor centerEditor = new CenterEditor();
	private boolean wrapText;
	private final Map<Tab, Map<Entry, Stack<View>>> tabEntryViewHistoryMap = new HashMap<>();
	private Map<Entry, Stack<View>> entryViewHistoryMap;
	private TextArea combinedTextArea;
	private View currentView;

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

		currentTabProperty.addListener((p, o, n) -> tabChange(n));
		selectedItemProperty.addListener((p, o, n) -> changeEntry((Entry)n, Optional.ofNullable(entryViewHistoryMap.get(n)).filter(s -> !s.isEmpty()).map(Stack::pop).orElse(View.CENTER)));

		disableProperty().bind(selectedItemProperty.isNull());

		setId("editor");
		addClass(maintitle,"main-title");

		maintitle.setPadding(new Insets(10));
		maintitle.setMaxWidth(Double.MAX_VALUE);
		maintitle.setWrapText(true);

		HBox hb = new HBox( 
				backBtn = button("back", "Back_30px.png", e -> historyBack()),
				maintitle,
				combineContentBtn = button("combine children content", "Plus Math_20px.png", e -> changeEntry(View.COMBINED_TEXT)),
				combineChildrenBtn = button("combine children view", "Cells_20px.png", e -> changeEntry(View.COMBINED_CHILDREN))
				);

		backBtn.setVisible(false);
		combineChildrenBtn.setVisible(false);
		combineContentBtn.setVisible(false);

		setTop(hb);
		addClass(hb, "control-box");
		hb.setAlignment(Pos.CENTER);
		HBox.setHgrow(maintitle, Priority.ALWAYS);
	}
	private void changeEntry(View view) {
		changeEntry(centerEditor.getItem(), view, false);
	}
	private void changeEntry(Entry item, View view) {
		changeEntry(item, view, false);
	}
	private void historyBack() {
		Optional.ofNullable(entryViewHistoryMap.get(centerEditor.getItem()))
		.filter(s -> !s.isEmpty())
		.map(Stack::pop)
		.ifPresent(v -> changeEntry(centerEditor.getItem(), v, true));
	}
	private void changeEntry(Entry item, View view, boolean skipHistory) {
		if(item == null) {
			resizeContainer(0);
			maintitle.setText(null);
			setCenter(null);
			entryViewHistoryMap.remove(item);
			backBtn.setVisible(false);
			combineChildrenBtn.setVisible(false);
			combineContentBtn.setVisible(false);
			currentView = null;
			return;
		}

		if(view == null || (view == View.COMBINED_CHILDREN && item.getChildren().isEmpty()))
			view = View.CENTER;

		setCenter(null);

		if(view == View.CENTER || view == View.EXPANDED) {
			setCenter(centerEditor);
			centerEditor.setItem(item);
			maintitle.setText(centerEditor.getItemTitle());
			if(view == View.CENTER) {
				currentView = null;
				entryViewHistoryMap.remove(item);
			}
		}
		else if(view == View.COMBINED_TEXT) {
			if(combinedTextArea == null) {
				combinedTextArea = new TextArea();
				combinedTextArea.setEditable(false);
			}
			combinedTextArea.setFont(Editor.getFont());
			combinedTextArea.setWrapText(wrapText);
			setCenter(combinedTextArea);
			combinedTextArea.setText(combineChildrenContent(item));
		}
		else if(view == View.COMBINED_CHILDREN) {
			createContainer();
			resizeContainer(item.getChildren().size() + 1);
			getUnitEditorAt(0).setItem(item);
			int index = 1;
			for (TreeItem<String> ti : item.getChildren()) getUnitEditorAt(index++).setItem((Entry)ti); 
			
			setCenter(containerScrollPane);
			containerScrollPane.setVvalue(0);
			maintitle.setText(item.getTitle());
		}
		if(!skipHistory && currentView != null) {
			Stack<View> s = entryViewHistoryMap.get(item);

			if(s == null)
				entryViewHistoryMap.put(item, s = new Stack<>());

			s.push(currentView);
		}

		currentView = view;

		backBtn.setVisible(Optional.ofNullable(entryViewHistoryMap.get(item)).filter(s -> !s.isEmpty()).isPresent());
		combineChildrenBtn.setVisible(view != View.COMBINED_CHILDREN && view != View.EXPANDED && !item.getChildren().isEmpty());
		combineContentBtn.setVisible(view != View.COMBINED_TEXT && view != View.EXPANDED && !item.getChildren().isEmpty());
	}
	public String combineChildrenContent(Entry item) {
		StringBuilder sb = new StringBuilder();

		combine(sb, item);

		item.getChildren().stream()
		.map(Entry::cast)
		.reduce(sb, this::combine, StringBuilder::append);

		return sb.toString();
	}

	public VBox createContainer() {
		container = new VBox(15);
		containerScrollPane = new ScrollPane(container);
		containerScrollPane.setFitToWidth(true);
		addClass(container,"container");
		container.setPadding(new Insets(10, 0, 10, 0));

		return container;
	}

	private StringBuilder combine(StringBuilder sb, Entry u) {
		char[] chars = new char[u.getTitle().length() + 10];
		Arrays.fill(chars, '#');
		sb.append(chars).append('\n')
		.append("     ").append(u.getTitle()).append('\n')
		.append(chars).append('\n').append('\n')
		.append(u.getContent()).append('\n').append('\n');

		return sb;
	}

	private UnitEditor getUnitEditorAt(int index) {
		return (UnitEditor)container.getChildren().get(index);
	}
	/* TODO
	 * 	private void backToContentAction() {
		if(combinedTextArea != null) {
			combinedTextArea.setText(null);
			combinedTextArea.setUserData(null);
		}
		if(getCenter() == centerEditor && backToContent.get() == containerScrollPane) {
			containerChildren()
			.filter(u -> u.getItem() == centerEditor.getItem())
			.findFirst()
			.ifPresent(u -> u.setItem(centerEditor.getItem()));
		}

		setCenter(backToContent.get());
		backToContent.set(null);
	}
	 */

	private void tabChange(Tab tab) {
		resizeContainer(0);
		maintitle.setText(null);
		centerEditor.clear();
		setCenter(null);
		entryViewHistoryMap = tabEntryViewHistoryMap.get(tab);
		if(entryViewHistoryMap == null)
			tabEntryViewHistoryMap.put(tab, entryViewHistoryMap = new HashMap<>());
	}

	private final WeakStore<UnitEditor> unitEditors = new WeakStore<>(() -> new UnitEditor(onExpanded));
	private void resizeContainer(int newSize) {
		if(container == null && newSize == 0)
			return;

		List<Node> list = container.getChildren();

		if(container.getChildren().size() > newSize) {
			list = list.subList(newSize, list.size());
			list.forEach(n -> unitEditors.add((UnitEditor)n));
			list.clear();
		}
		else {
			while(list.size() != newSize)
				list.add(unitEditors.get());

			for (Node n : list) {
				UnitEditor e = (UnitEditor)n;
				e.setWordWrap(wrapText);
				e.updateTitle();
				e.updateFont();
			}
		}
	}
	public void updateTitle(Entry item) {
		containerChildren().forEach(UnitEditor::updateTitle);
		centerEditor.updateTitle();
		if(getCenter() == centerEditor)
			maintitle.setText(centerEditor.getItemTitle());
		else if(container == null || container.getChildren().isEmpty())
			maintitle.setText(null);
		else
			maintitle.setText(getUnitEditorAt(0).getItemTitle());
	}

	private Stream<UnitEditor> containerChildren() {
		return container == null ? Stream.empty() : 
			container.getChildren().stream()
			.map(n -> (UnitEditor)n);
	}
	public void setWordWrap(boolean wrap) {
		wrapText = wrap;
		centerEditor.setWordWrap(wrap);
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
			containerChildren().forEach(UnitEditor::updateFont);

			Session.put("editor.font.family", family.getValue());
			Session.put("editor.font.weight",weight.getValue().toString());
			Session.put("editor.font.posture",posture.getValue().toString());
			Session.put("editor.font.size", size.getValue().toString());
		});
		stage.showAndWait();
	}
}
