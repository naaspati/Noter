package sam.noter.dao;

import static sam.noter.dao.VisitResult.CONTINUE;
import static sam.noter.dao.VisitResult.SKIP_SIBLINGS;
import static sam.noter.dao.VisitResult.TERMINATE;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.logging.MyLoggerFactory;

public abstract class Entry extends TreeItem<String> {
	private static final Logger LOGGER = MyLoggerFactory.logger(Entry.class);

	final int id;
	protected String content;
	protected long lastModified = -1;

	protected Supplier<String> contentProxy;
	protected final ObservableList<TreeItem<String>> items;
	private ObservableList<TreeItem<String>> unmodifiable;

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

	protected boolean setTitle(String title) {
		if(!Objects.equals(title, getTitle())) {
			LOGGER.fine(() -> "TITLE MODIFIED: "+this);
			setValue(title);
			return true;
		}
		return false;
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
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content;
	}

	protected  boolean setContent(String content) {
		if(!Objects.equals(content, this.content)) {
			this.content = content;
			updateLastmodified();
			return true;
		}
		return false;
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
	protected ObservableList<TreeItem<String>> getModifiableChildren() {
		return items;
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
			return VisitResult.CONTINUE;
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
	public String toTreeString() {
		String s = parent().toTreeString(); 
		return s == null ? getTitle() : s +" > "+getTitle();
	}
}