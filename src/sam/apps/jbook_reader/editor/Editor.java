package sam.apps.jbook_reader.editor;

import static sam.apps.jbook_reader.Utils.addClass;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import sam.apps.jbook_reader.tabs.Tab;

public class Editor extends BorderPane {
	private final VBox container = new VBox(15);
	private final ScrollPane containerScrollPane = new ScrollPane(container);
	private final Label maintitle = new Label();
	private final ReadOnlyObjectProperty<Tab> currentTabProperty;
	private UnitEditor activeEditor;
	private Consumer<UnitEditor> onUnitEditorSelected = this::onUnitEditorSelected;
	
	private static transient Editor instance;

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
}
