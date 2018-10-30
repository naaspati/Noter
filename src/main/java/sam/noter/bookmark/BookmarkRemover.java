package sam.noter.bookmark;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import sam.noter.Utils;
import sam.noter.tabs.Tab;
public class BookmarkRemover {
	private HashMap<Tab, LinkedList<PatrentChildRelation[]>> deletedItems = new HashMap<>();
	final SimpleIntegerProperty undoDeleteSize = new SimpleIntegerProperty();
	
	public void removeAction(MultipleSelectionModel<TreeItem<String>> selectionModel, Tab tab) {
		if(selectionModel.getSelectedItems().isEmpty())
			return;

		LinkedList<PatrentChildRelation[]> list = deletedItems.get(tab);
		if(list == null) {
			list = new LinkedList<>();
			deletedItems.put(tab, list);
		}
		PatrentChildRelation[] ditems = selectionModel.getSelectedItems().stream()
				.map(Utils::castEntry)
				.map(PatrentChildRelation::new)
				.toArray(PatrentChildRelation[]::new);

		list.add(ditems);
		undoDeleteSize.set(list.size());

		for (PatrentChildRelation d : ditems) d.removeChildFromParent();
	}
	public void tabClosed(Tab tab) {
		deletedItems.remove(tab);
	}
	public void undoRemoveBookmark(Tab tab) {
		if(undoDeleteSize.get() == 0)
			return;

		Optional.ofNullable(tab)
		.map(deletedItems::get)
		.map(LinkedList::pollLast)
		.ifPresent(items -> {
			for (int i = items.length - 1; i >= 0 ; i--)
				items[i].addChildToParent();

			undoDeleteSize.set(deletedItems.get(tab).size());
		});

	}
	public void switchTab(Tab newTab) {
		int size = Optional.ofNullable(newTab)
				.map(deletedItems::get)
				.map(List::size)
				.orElse(0);

		undoDeleteSize.set(size);
	}
}
