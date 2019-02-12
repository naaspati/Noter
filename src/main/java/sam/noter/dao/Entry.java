package sam.noter.dao;

import static sam.noter.dao.ModifiedField.CONTENT;
import static sam.noter.dao.ModifiedField.TITLE;
import static sam.noter.dao.VisitResult.CONTINUE;
import static sam.noter.dao.VisitResult.SKIP_SIBLINGS;
import static sam.noter.dao.VisitResult.TERMINATE;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.LogManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public abstract class Entry extends TreeItem<String> {
	private static final Logger logger = LogManager.getLogger(Entry.class);

	public final int id;
	protected String content;
	protected long lastModified = -1;
	protected boolean titleM, contentM, childrenM;

	protected Supplier<String> contentProxy;
	protected final ObservableList<TreeItem<String>> items;
	protected final ObservableList<TreeItem<String>> unmodifiable;

	protected Entry(int id) {
		super();
		this.id = id;
		items = super.getChildren();
		unmodifiable = FXCollections.unmodifiableObservableList(items); 
	}
	protected Entry(int id, String title, String content, long lastModified) {
		this(id, title);
		this.content = content;
		this.lastModified = lastModified;
	}
	public Entry(int id, String title) {
		this(id);
		setValue(title);
	}
	protected Entry(int id, Entry from) {
		this(id);
		this.content = from.getContent();
		super.setValue(from.getTitle());
		this.lastModified = from.getLastModified();

		titleM = true;
		contentM = true;
		childrenM = true;
	}
	public int getId() {
		return id;
	}
	public void setContentProxy(Supplier<String> contentProxy) {
		this.contentProxy = contentProxy;
	}
	protected void clearModified() {
		titleM = false;
		contentM = false;
		childrenM = false;
	}
	public void setTitle(String title) {
		if(titleM || notEqual(title, getTitle())) {
			logger.debug(() -> "TITLE MODIFIED: "+this);
			super.setValue(title);
			titleM = true;
			notifyParent(TITLE);
		}
	}
	protected boolean notEqual(String s1, String s2) {
		return !Objects.equals(s1, s2);
	}
	public long getLastModified() {
		return lastModified;
	}
	protected void updateLastmodified() {
		setLastModified(System.currentTimeMillis());
	}
	protected void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}
	public String getTitle() { return getValue(); }
	
	/**
	 * specialMethod used by App.combineEverything.walk
	 * return content without keeping the reference
	 * @param cacheContent
	 * @return
	 */
	public String getContentWithoutCaching() {
		throw new IllegalAccessError("not implemeted");
	}
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content == null ? "" : content;
	}

	public  void setContent(String content) {
		if(contentM || notEqual(content, this.content)) {
			this.content = content;
			contentM = true;
			updateLastmodified();
			notifyParent(CONTENT);
			logger.debug(() -> "CONTENT MODIFIED: "+this);
		}
	}

	/* ###############################################
	 *                    children modifications
	 * ###############################################
	 */

	/**
	 * 
	 * @param field -> name of field which is modified in modifiedEntry  
	 * @param child -> child of current parent which propogated the change notification 
	 * @param modifiedEntry
	 */
	protected void childModified(ModifiedField field, Entry child, Entry modifiedEntry) {
		childrenM = true;
		parent().childModified(field, this, modifiedEntry);
	}
	protected void notifyParent(ModifiedField field) {
		Entry p = parent();
		if(p != null) 
			p.childModified(field, this, this);
	}

	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		return unmodifiable;
	}
	protected void modifiableChildren(Consumer<List<TreeItem<String>>> modify) {
		modify.accept(items);
		childrenM = true;
		notifyParent(ModifiedField.CHILDREN);
	}

	/* ###############################################
	 *                    GENERAL
	 * ###############################################
	 */

	public int size() {
		return getChildren().size();
	}
	public int indexOf(Entry item) {
		return getChildren().indexOf(item);
	}
	public boolean isEmpty() {
		return getChildren().isEmpty();
	}

	public void walk(Consumer<Entry> consumer) {
		walkTree(w -> {
			consumer.accept(w);
			return CONTINUE;
		});
	}

	public void walkTree(Walker walker) {
		walk0(this, walker);
	}
	private VisitResult walk0(Entry parent, Walker walker) {
		if(parent.getChildren().isEmpty()) return CONTINUE;

		for (TreeItem<String> item : parent.getChildren()) {
			Entry e = (Entry) item;
			VisitResult v = walker.accept(e);

			switch (v) {
				case CONTINUE:
					v = walk0(e, walker);
					if(v == TERMINATE) return TERMINATE;
					if(v == SKIP_SIBLINGS) return CONTINUE;
					break;

				case SKIP_SIBLINGS: return CONTINUE;
				case SKIP_SUBTREE: break;
				case TERMINATE: return TERMINATE;
			}
		}
		return CONTINUE;
	}

	public String toString() {
		return getClass().getSimpleName()+" {id:"+id+", title:\""+getValue()+"\"}";
	}
	public Entry parent() {
		return (Entry)getParent();
	}
	public String toTreeString(boolean includeRootName) {
		Entry e = parent();
		if(e == null)
			return includeRootName ? getValue() : null;
		String s = e.toTreeString(includeRootName); 
		return s == null ? getValue() : s +" > "+getValue();
	}
	public boolean isModified() {
		return titleM || contentM || childrenM;
	}
	public boolean isTitleModified() {
		return titleM;
	}
	public boolean isContentModified() {
		return contentM;
	}
	public boolean isChildrenModified() {
		return childrenM;
	}

	@SuppressWarnings("unchecked")
	protected void addAll(@SuppressWarnings("rawtypes") List child, int index) {
		modifiableChildren(list -> {
			if(index <= 0)
				list.addAll(0, child);
			else if(index >= size())
				list.addAll(child);
			else
				list.addAll(index, child);
		});
	}
	protected void add(Entry child, int index) {
		modifiableChildren(list -> {
			if(index <= 0)
				list.add(0, child);
			else if(index >= size())
				list.add(child);
			else
				list.add(index, child);			
		});
	}
	@Override
	public final int hashCode() {
		return id;
	}
	@Override
	public final boolean equals(Object obj) {
		if(obj == this) return true;
		if(obj == null || obj.getClass() != getClass() || this.id != ((Entry)obj).id) return false;

		if(getRoot0() == ((Entry)obj).getRoot0())
			throw new IllegalStateException("two different entry have same id"+this+", "+obj);
		return false;
	}
	
	protected abstract Entry getRoot0();
	public abstract boolean isContentLoaded();
}