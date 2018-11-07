package sam.noter.dao;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface RootEntry extends AutoCloseable {
	File getJbookPath();
	void setJbookPath(File path);

	boolean isModified();

	void reload() throws Exception;
	void save(File file) throws Exception;
	default void save() throws Exception {
		save(getJbookPath());
	};

	void setTitle(Entry entry, String title);
	void setContent(Entry entry, String content);
	
	void setOnModified(Runnable action);
	
	default Entry addChild(String childTitle, Entry parent, Entry relativeToChild) {
		int index = relativeToChild == null ? Integer.MAX_VALUE  : parent.indexOf(relativeToChild);
		if(index < 0)
			throw new IllegalArgumentException(relativeToChild+" is not a child of "+parent);
		
		return addChild(childTitle, parent, index);
	}
	default Entry addChild(String childTitle, Entry parent) {
		return addChild(childTitle, parent, Integer.MAX_VALUE);
	}
	
	Entry addChild(String title, Entry parent,  int index);

	/**
	 * add given children to newParent, return added children (they might have been modified)
	 * @param childrenToMove
	 * @param newParent
	 * @param index
	 * @return
	 */
	List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index);
	public Map<Integer, Entry> getEntriesMap();
}
