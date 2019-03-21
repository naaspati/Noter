package sam.noter.tabs;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Menu;
import javafx.scene.layout.HBox;
import sam.nopkg.Junk;
import sam.noter.dao.api.IRootEntry;

public class TabBox extends HBox {
	private final ChoiceBox<IRootEntry> root = new ChoiceBox<>();
	private final IntegerBinding countProperty = Bindings.size(root.getItems());
	
	public ObservableValue<IRootEntry> selectedItemProperty() {
		return root.getSelectionModel().selectedItemProperty();
	}

	public void forEach(Consumer<IRootEntry> action) {
		items().forEach(action);
	}

	public void addTab(IRootEntry entry) {
		items().add(entry);
	}

	public boolean closeAll() {
		return Junk.notYetImplemented();
		// TODO Auto-generated method stub		
	}

	public IntegerBinding tabsCountProperty() {
		return countProperty;
	}

	public void closeExcept(IRootEntry t) {
		// TODO Auto-generated method stub
	}

	public void closeRightLeft(IRootEntry t, boolean b) {
		// TODO Auto-generated method stub
	}

	public void closeTab(IRootEntry t) {
		items().remove(t);
	}

	public void addBlankTab() {
		// TODO Auto-generated method stub
	}

	public void addListener(ListChangeListener<IRootEntry> listener) {
		items().addListener(listener);
	}

	private ObservableList<IRootEntry> items() {
		return this.root.getItems();
	}
}

