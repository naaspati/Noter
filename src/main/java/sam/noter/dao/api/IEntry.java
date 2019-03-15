package sam.noter.dao.api;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import sam.noter.dao.ModifiedField;
import sam.noter.dao.Walker;

public interface IEntry {
	List<IEntry> EMPTY = Collections.emptyList();
	
	boolean isModified(ModifiedField field);
	int getId();
	IRootEntry root();
	Collection<? extends IEntry> getChildren();
	void walk(Walker<IEntry> consumer);
	
	void setTitle(String title);
	void setContent(String content);
	
	String getTitle();
	String getContent();
	long getLastModified();
	IEntry getParent();
}
