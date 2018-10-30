package sam.noter.dao;

import java.io.File;
import java.util.stream.Stream;

public interface RootEntry {
	File getJbookPath();
	void setJbookPath(File path);
	
	boolean isModified();
	
	void reload() throws Exception;
	void save(File file) throws Exception;
	default void save() throws Exception {
		save(getJbookPath());
	};
	
	void setOnModified(Runnable action);
	
	public Stream<Entry> walk();
}
