package sam.noter.dao.api;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import sam.myutils.Checker;
import sam.noter.dao.Walker;

public interface IRootEntry extends Closeable {
	Path getJbookPath();
	// TODO void setJbookPath(Path path);
	
	int modCount();

	void reload() throws Exception;
	void save(Path file) throws Exception;
	
	default void save() throws Exception {
		save(getJbookPath());
	};
	
	@Deprecated
	default IEntry addChild(String childTitle, IEntry parent, IEntry relativeToChild) {
		int index = relativeToChild == null ? Integer.MAX_VALUE  : parent.indexOf(relativeToChild);
		Checker.assertTrue(index >= 0, () -> new IllegalArgumentException(relativeToChild+" is not a child of "+parent));
		return addChild(childTitle, parent, index+1);
	}
	@Deprecated
	default IEntry addChild(String childTitle, IEntry parent) {
		return addChild(childTitle, parent, Integer.MAX_VALUE);
	}
	
	
	

	/**
	 * add given children to newParent, return added children (they might have been modified)
	 * @param childrenToMove
	 * @param newParent
	 * @param index
	 * @return
	 */
	void moveChild(List<IEntry> childrenToMove, IEntry newParent, int index);
	IEntry addChild(String title, IEntry parent, int index);
	void addChild(IEntry child, IEntry parent, int index);
	void removeFromParent(IEntry child);
	Collection<? extends IEntry> getChildren();
	void forEachFlattened(Consumer<IEntry> consumer);
	IEntry getEntryById(int id);
	void walk(Walker<IEntry> walker);
	String getTitle();

	boolean isModified();
}
