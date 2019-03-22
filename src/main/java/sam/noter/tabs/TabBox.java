package sam.noter.tabs;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.HBox;
import sam.noter.dao.api.IRootEntry;

public class TabBox extends HBox implements Iterable<IRootEntry> {
	private final ChoiceBox<IRootEntry> root = new ChoiceBox<>();
	private final IntegerBinding countProperty = Bindings.size(root.getItems());
	private final ObservableList<IRootEntry> items = this.root.getItems();
	
	public ObservableValue<IRootEntry> selectedItemProperty() {
		return root.getSelectionModel().selectedItemProperty();
	}

	@Override
	public void forEach(Consumer<IRootEntry> action) {
		items.forEach(action);
	}
	@Override
	public Iterator<IRootEntry> iterator() {
		return items.iterator();
	}

	public void addTab(IRootEntry entry) {
		items.add(entry);
	}
	
	/**
	 * 
	 * @param filter
	 * @return number of closed tabs
	 */
	public int closeIf(Predicate<IRootEntry> filter) {
		if(openCount() == 0)
			return 0;
		int n[] = {0};
		items.removeIf(f -> {
			if(filter.test(f) && confirmClose(f)) {
				n[0]++;
				return true;
			}
			return false;
		});
		
		return n[0];
	}
	public boolean closeTab(IRootEntry t) {
		int index = items.indexOf(t);
		
		if(index < 0)
			return false;
		
		if(confirmClose(t)) {
			items.remove(index);
			return true;
		}
		
		return false;
	}
	
	private boolean confirmClose(IRootEntry f) {
		// TODO Auto-generated method stub
		return false;
	}

	public int openCount() {
		return items.size();
	}
	public IntegerBinding tabsCountProperty() {
		return countProperty;
	}
	public void addListener(ListChangeListener<IRootEntry> listener) {
		items.addListener(listener);
	}
}

