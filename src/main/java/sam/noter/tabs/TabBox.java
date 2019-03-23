package sam.noter.tabs;

import java.util.Iterator;
import java.util.function.Predicate;

import javax.inject.Singleton;

import org.slf4j.Logger;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.HBox;
import sam.fx.alert.FxAlert;
import sam.nopkg.EnsureSingleton;
import sam.noter.ActionResult;
import sam.noter.Utils;
import sam.noter.dao.api.IRootEntry;

@Singleton
public class TabBox extends HBox implements Iterable<IRootEntry> {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	private static final Logger logger = Utils.logger(TabBox.class);

	{
		singleton.init();
	}

	private final ChoiceBox<IRootEntry> root = new ChoiceBox<>();
	private final IntegerBinding countProperty = Bindings.size(root.getItems());
	private final ObservableList<IRootEntry> items = this.root.getItems();

	public ObservableValue<IRootEntry> selectedItemProperty() {
		return root.getSelectionModel().selectedItemProperty();
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
			if(filter.test(f) && close(f)) {
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

		if(close(t)) {
			items.remove(index);
			return true;
		}

		return false;
	}

	private boolean close(IRootEntry f) {
		if(f.isModified()) {
			ActionResult result = FxAlert.alertBuilder(AlertType.CONFIRMATION)
					.title("Save File")
					.content(f.getJbookPath() != null ? f.getJbookPath() : f.getTitle())
					.buttons(ButtonType.NO, ButtonType.YES, ButtonType.CANCEL)
					.showAndWait()
					.map(b -> b == ButtonType.YES ? ActionResult.YES : 
						b == ButtonType.NO ? ActionResult.NO : 
							ActionResult.CANCEL)
					.orElse(ActionResult.NULL);
			
			if(result == ActionResult.CANCEL || result == ActionResult.NULL)
				return false;
			
			if(result == ActionResult.YES) {
				if(!errorHandle(f, f::save, "failed to save: "))
					return false;
			} 
		}
		
		return errorHandle(f, f::close, "failed to close: ");
	}
	
	private static interface Temp {
		public void call() throws Exception;
	}

	private boolean errorHandle(IRootEntry f, Temp action, String failMsg) {
		try {
			action.call();
			return true;
		} catch (Exception e) {
			logger.error(failMsg+" {} -> {}", f.getTitle(), f.getJbookPath(), e);
			FxAlert.showErrorDialog(f.getJbookPath(), failMsg+"\n"+f.getTitle(), e);
			return FxAlert.showConfirmDialog("force close failed tab", "Close anyways");
		}
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

