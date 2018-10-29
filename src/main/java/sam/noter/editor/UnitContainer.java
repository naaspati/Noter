package sam.noter.editor;

import static sam.fx.helpers.FxClassHelper.addClass;

import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import sam.noter.datamaneger.Entry;
import sam.reference.WeakList;

class UnitContainer extends ScrollPane {
	private final VBox root = new VBox(15);
	private final WeakList<UnitEditor> unitEditors;
	private boolean wrapText;
	private Entry item;
	
	public UnitContainer(Consumer<Entry> onExpanded) {
		unitEditors  = new WeakList<>(() -> new UnitEditor(onExpanded));
		
		setContent(root);
		setFitToWidth(true);
		addClass(root,"container");
		this.root.setPadding(new Insets(10, 0, 10, 0));
	}
	
	private void resizeContainer(int newSize) {
		List<Node> list = root.getChildren();

		if(root.getChildren().size() > newSize) {
			list = list.subList(newSize, list.size());
			list.forEach(n -> unitEditors.add((UnitEditor)n));
			list.clear();
		}
		else {
			while(list.size() != newSize)
				list.add(unitEditors.poll());

			for (Node n : list) {
				UnitEditor e = (UnitEditor)n;
				e.setWordWrap(wrapText);
				e.updateTitle();
				e.updateFont();
			}
		}
	}
	public void setWordWrap(boolean wrap) {
		wrapText = wrap;
		forEach(u -> u.setWordWrap(wrap));
	}
	
	private void forEach(Consumer<UnitEditor> action) {
		root.getChildren().forEach(n -> action.accept((UnitEditor)n));
	}
	public void updateTitle(Entry item) {
		forEach(UnitEditor::updateTitle);
	}
	public boolean isEmpty() {
		return root.getChildren().isEmpty();
	}
	public UnitEditor first() {
		return (UnitEditor) root.getChildren().get(0);
	}
	public void updateFont() {
		forEach(UnitEditor::updateFont);
	}

	public void clear() {
		forEach(unitEditors::add);
		root.getChildren().clear();
	}

	public void setItem(Entry item) {
		this.item = item;
		resizeContainer(item.getChildren().size() + 1);
		first().setItem(item);
		int index = 1;
		for (TreeItem<String> ti : item.getChildren()) getUnitEditorAt(index++).setItem((Entry)ti); 

		setVvalue(0);
	}
	private UnitContainer getUnitEditorAt(int i) {
		return (UnitContainer) root.getChildren().get(i);
	}

	public Entry getItem() {
		return item;
	}
}
