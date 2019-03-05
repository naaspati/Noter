package sam.noter.dao.api;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import sam.noter.dao.ModifiedField;

public interface IEntry {
	List<IEntry> EMPTY = Collections.emptyList();
	
	boolean isModified(ModifiedField field);
	int getId();
	IEntry parent();
	IRootEntry root();
	List<IEntry> getChildren();
	void walk(Consumer<IEntry> consumer);
	
	void setTitle(String title);
	void setContent(String content);
	
	String getTitle();
	String getContent();
	long getLastModified();
	IEntry getParent();
}
