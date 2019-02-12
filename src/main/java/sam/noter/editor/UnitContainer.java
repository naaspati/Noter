package sam.noter.editor;

import static sam.fx.helpers.FxClassHelper.addClass;

import java.util.List;
import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;
import sam.noter.dao.Entry;
import sam.reference.WeakPool;

class UnitContainer extends ScrollPane {
	private final VBox root = new VBox(15);
	private final WeakPool<UnitEditor> unitEditors;
	private boolean wrapText;
	private Entry item;
	private final ObservableList<Node> list;
	
	public UnitContainer(Consumer<Entry> onExpanded) {
		unitEditors  = new WeakPool<>(() -> new UnitEditor(onExpanded));
		
		setContent(root);
		list = root.getChildren();
		setFitToWidth(true);
		addClass(root,"container");
		this.root.setPadding(new Insets(10, 0, 10, 0));
	}
	
	private void resizeContainer(int newSize) {
		if(root.getChildren().size() > newSize) {
			List<Node> list2 = list.subList(newSize, list.size());
			list2.forEach(n -> unitEditors.add((UnitEditor)n));
			list2.clear();
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
		list.forEach(n -> action.accept((UnitEditor)n));
	}
	public void updateTitle(Entry item) {
		forEach(UnitEditor::updateTitle);
	}
	public boolean isEmpty() {
		return list.isEmpty();
	}
	public UnitEditor first() {
		return (UnitEditor) list.get(0);
	}
	public void updateFont() {
		forEach(UnitEditor::updateFont);
	}
	public void clear() {
		forEach(unitEditors::add);
		list.clear();
	}

	public void setItem(Entry item) {
		this.item = item;
		resizeContainer(item.getChildren().size() + 1);
		first().setItem(item);
		int index = 1;
		for (TreeItem<String> ti : item.getChildren()) ((UnitEditor) list.get(index++)).setItem((Entry)ti); 

		setVvalue(0);
	}
	public Entry getItem() {
		return item;
	}
}
