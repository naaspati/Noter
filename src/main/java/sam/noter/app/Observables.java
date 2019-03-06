package sam.noter.app;

import javafx.beans.value.ObservableValue;
import sam.noter.EntryTreeItem;
import sam.noter.dao.api.IRootEntry;

public interface Observables {
	public ObservableValue<IRootEntry> currentRootEntryProperty();
	public ObservableValue<EntryTreeItem> currentItemProperty();
}
