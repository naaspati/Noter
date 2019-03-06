package sam.noter.dao;

import static sam.noter.dao.ModifiedField.CONTENT;
import static sam.noter.dao.ModifiedField.TITLE;
import static sam.noter.dao.VisitResult.CONTINUE;
import static sam.noter.dao.VisitResult.SKIP_SIBLINGS;
import static sam.noter.dao.VisitResult.TERMINATE;

import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;

import sam.noter.dao.api.IEntry;

public abstract class Entry implements IEntry {

	protected final int id;

	protected IEntry parent;
	protected String title; 
	protected String content;
	protected long lastModified = -1;
	protected List<IEntry> children;

	protected Entry(int id) {
		this.id = id;
	}
	protected Entry(int id, String title, String content, long lastModified) {
		this(id, title);
		this.content = content;
		this.lastModified = lastModified;
	}
	public Entry(int id, String title) {
		this.id = id;
		this.title = title;
	}
	protected Entry(int id, Entry from) {
		this(id, from.getTitle(), from.getContent(), from.getLastModified());
	}
	
	protected abstract void setModified(ModifiedField field, boolean value);
	protected abstract Logger logger();

	public int getId() {
		return id;
	}
	protected void clearModified() {
		setModified(ModifiedField.ALL, false);
	}
	@Override
	public void setTitle(String title) {
		if(!isModified(TITLE)) {
			logger().debug("TITLE MODIFIED: {}", this);
			setModified(TITLE, true);
		}
		this.title = title;
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
	public String getTitle() { return title; }
	public String getContent() { return content == null ? "" : content; }

	public void setContent(String content) {
		if(!isModified(CONTENT)) {
			logger().debug("CONTENT MODIFIED: {}", this);
			setModified(CONTENT, true);
		}
		this.content = content;
	}

	public String toString() {
		return getClass().getSimpleName()+" {id:"+id+", title:\""+title+"\"}";
	}
	public Entry parent() {
		return (Entry)parent;
	}

	public void walk(Consumer<IEntry> consumer) {
		List<IEntry> list = getChildren();
		if(!list.isEmpty()) {
			list.forEach(c -> {
				consumer.accept(c);
				c.walk(consumer);
			});
		}
	}
	@Override
	public void walk(Walker<IEntry> walker) {
		walk0(this, walker);
	}
	private VisitResult walk0(IEntry parent, Walker<IEntry> walker) {
		if(parent.getChildren().isEmpty()) 
			return CONTINUE;

		for (IEntry item : parent.getChildren()) {
			IEntry e = item;
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
	@Override
	public final int hashCode() {
		return id;
	}
	@Override
	public final boolean equals(Object obj) {
		if(obj == this) return true;
		if(obj == null || obj.getClass() != getClass() || this.id != ((Entry)obj).id) return false;

		if(root() == ((Entry)obj).root())
			throw new IllegalStateException("two different entry have same id"+this+", "+obj);
		return false;
	}
	@Override
	public List<IEntry> getChildren() {
		return children;
	}
	
}