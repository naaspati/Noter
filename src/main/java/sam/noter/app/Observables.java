package sam.noter.app;

import javafx.beans.value.ObservableValue;
import sam.noter.dao.api.IRootEntry;

public interface Observables {
	public ObservableValue<IRootEntry> currentRootEntryProperty();
}
