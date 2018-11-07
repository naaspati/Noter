package sam.noter.dao;

import static sam.noter.dao.EntryField.CHILDREN;
import static sam.noter.dao.EntryField.CONTENT;
import static sam.noter.dao.EntryField.LAST_MODIFIED;
import static sam.noter.dao.EntryField.TITLE;
import static sam.noter.dao.EntryField.values;
import static sam.noter.dao.VisitResult.CONTINUE;
import static sam.noter.dao.VisitResult.SKIP_SIBLINGS;
import static sam.noter.dao.VisitResult.TERMINATE;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.logging.MyLoggerFactory;

public abstract class Entry extends TreeItem<String> {
	private static final Logger LOGGER = MyLoggerFactory.logger(Entry.class.getSimpleName());
	private static final int SIZE = values().length;
	protected boolean[] modified;
	
	final int id;
	protected String content;
	protected long lastModified = -1;

	protected Supplier<String> contentProxy;
	protected final ObservableList<TreeItem<String>> items;
	protected ObservableList<TreeItem<String>> unmodifiable;

	protected Entry(int id) {
		super();
		this.id = id;
		items = super.getChildren();
	}
	protected Entry(int id, String title, String content, long lastModified) {
		this(id, title);
		this.content = content;
		this.lastModified = lastModified;
	}
	public Entry(int id, String title) {
		super(title);
		this.id = id;
		items = super.getChildren();
	}
	public int getId() {
		return id;
	}
	public void setContentProxy(Supplier<String> contentProxy) {
		this.contentProxy = contentProxy;
	}

	public void setTitle(String title) {
		if(isModified(TITLE) || !Objects.equals(title, getTitle())) {
			LOGGER.fine(() -> "TITLE MODIFIED: "+this);
			setValue(title);
			notifyModified(TITLE);
		}
	}
	public boolean isModified(EntryField field) {
		return modified != null && modified[field.ordinal()];
	}
	protected void setModified(EntryField field, boolean b) {
		if(!b && modified == null) return;
		
		if(modified == null) 
			modified = new boolean[SIZE];
		
		modified[field.ordinal()] = b;
	}

	public long getLastModified() {
		return lastModified;
	}
	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
		notifyModified(LAST_MODIFIED);
	}
	public String getTitle() { return getValue(); }
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content;
	}

	public  void setContent(String content) {
		if(isModified(CONTENT) || !Objects.equals(content, this.content)) {
			this.content = content; 
			lastModified = System.currentTimeMillis();
			notifyModified(CONTENT);
		}
	}

	/* ###############################################
	 *                    children modifications
	 * ###############################################
	 */

	protected abstract void loadChildren(@SuppressWarnings("rawtypes") List sink);
	
	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		if(unmodifiable == null) {
			loadChildren(items);
			unmodifiable = FXCollections.unmodifiableObservableList(items);
			LOGGER.fine(() -> "CHILDREN LOADED: "+this);
		}
		return unmodifiable;
	}
	protected boolean modifyChildren(Predicate<List<TreeItem<String>>> action) {
		boolean e = action.test(items);
		notifyModified(CHILDREN);
		return e;
	}

	protected  boolean remove(Entry t) {
		return modifyChildren(list -> list.remove(t));
	}
	protected  boolean add(Entry child) {
		return modifyChildren(list -> list.add(child));
	}
	protected  void add(int index, Entry child) {
		modifyChildren(list -> {
			if(index <= 0)
				list.add(0, child);
			else if(index >= size())
				list.add(child);
			else
				list.add(index, child);
			return true;
		});
	}
	protected  void addAll(int index, List<Entry> from) {
		modifyChildren(to -> {
			if(index <= 0)
				return to.addAll(0, from);

			if(index >= to.size())
				return to.addAll(from);

			return to.addAll(index, from);
		});
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
	public boolean isModified() {
		if(modified == null) return false;
		for (boolean b : modified) 
			if(b) return true;
		return false;
	}
	public void walk(Walker walker) {
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

	protected void notifyModified(EntryField type) {
		LOGGER.fine(() -> "MODIFIED: "+this);
		notifyModified(this, type, false);
	}
	/**
	 * notifies parent, that me/my_child is modified 
	 */
	protected void notifyModified(Entry modified, EntryField type, boolean fromChild) {
		setModified(type, true);
		
		Entry p = (Entry)getParent(); 
		if(p != null)
			p.notifyModified(modified, type, true);
	}
	protected void setAllModified(boolean b){
		if(!b) 
			modified = null;
		else {
			if(modified == null)
				modified = new boolean[SIZE];
			Arrays.fill(modified, true);
		}
	}
	@Override
	public String toString() {
		return getClass().getSimpleName()+" {id:"+id+", title:\""+getValue()+"\"}";
	}
	public Entry parent() {
		return (Entry)getParent();
	}
	public String toTreeString() {
		String s = parent().toTreeString(); 
		return s == null ? getTitle() : s +" > "+getTitle();
	}
}