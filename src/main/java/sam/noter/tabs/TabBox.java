package sam.noter.tabs;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Menu;
import javafx.scene.layout.HBox;
import sam.noter.dao.api.IRootEntry;

public class TabBox extends HBox {
	private final ChoiceBox<IRootEntry> root = new ChoiceBox<>();
	private final IntegerBinding countProperty = Bindings.size(root.getItems());
	
	public ObservableValue<IRootEntry> selectedItemProperty() {
		return root.getSelectionModel().selectedItemProperty();
	}

	public void forEach(Consumer<IRootEntry> action) {
		root.getItems().forEach(action);
	}

	public void addTabs(List<Path> files) {
		// TODO Auto-generated method stub
		
	}

	public void open(List<Path> list, BiConsumer<Path, IRootEntry> onSuccess) {
		// TODO Auto-generated method stub
	}

	public boolean closeAll() {
		// TODO Auto-generated method stub
		return false;
	}

	public IntegerBinding tabsCountProperty() {
		return countProperty;
	}

	public Object closeExcept(IRootEntry currentRoot) {
		// TODO Auto-generated method stub
		return null;
	}

	public Object closeRightLeft(IRootEntry currentRoot, boolean b) {
		// TODO Auto-generated method stub
		return null;
	}

	public Object closeTab(IRootEntry currentRoot) {
		// TODO Auto-generated method stub
		return null;
	}
}

