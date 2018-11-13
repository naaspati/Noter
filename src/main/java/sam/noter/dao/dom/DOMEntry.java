package sam.noter.dao.dom;

import java.util.List;
import java.util.function.Consumer;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;
import sam.noter.dao.dom.DOMLoader.DomEntryInit;

class DOMEntry extends Entry {
	private final DomEntryInit dom;
	
	public DOMEntry() {
		super(RootEntry.ROOT_ENTRY_ID);
		this.dom = null;
		if(getClass() != RootDOMEntry.class)
			throw new IllegalAccessError("can on be accessed by "+RootDOMEntry.class);
	}

	public DOMEntry(DomEntryInit init) {
		super(init.id(), init.title());
		this.dom = init;
	}
	public DOMEntry(DomEntryInit dom, String title, String content, long modified) {
		this(dom, title);
		this.content = content;
		this.lastModified = modified;
	}
	public DOMEntry(DomEntryInit dom, String title) {
		super(dom.id(), title);
		this.dom = dom;
		this.lastModified = System.currentTimeMillis();
	}
	DomEntryInit dom() {
		return dom;
	}
	RootDOMEntry getRoot() {
		return dom.getRoot();
	}
	@Override
	public long getLastModified() {
		if(lastModified == -1)
			lastModified = dom.lastModified();
		return lastModified;
	}
	@Override
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content_0();
	}
	private String content_0() {
		if(content == null)
			content = dom.content();

		return content;
	}
	
	private boolean childrenLoaded;
	
	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		if(!childrenLoaded) {
			childrenLoaded = true;
			dom.collectChildren(items);
		}
		return unmodifiable;
	}
	
	@Override public void setTitle(String title) { super.setTitle(title); }
	@Override public void updateLastmodified() { super.updateLastmodified(); }
	@Override public void setLastModified(long lastModified) { super.setLastModified(lastModified); }
	@Override public void setContent(String content) { super.setContent(content); }
	@Override public void modifiableChildren(Consumer<List<TreeItem<String>>> modify) { super.modifiableChildren(modify); }
	@SuppressWarnings("rawtypes")
	@Override protected void addAll(List child, int index) { super.addAll(child, index); }
	@Override protected void add(Entry child, int index) { super.add(child, index); }
	@Override protected void clearModified() { super.clearModified(); }
	
	@Override
	public boolean isModified() {
		return dom.isNew() || super.isModified();
	}
	@Override
	public boolean isContentLoaded() {
		return true;
	}
	@Override
	protected Entry getRoot0() {
		return getRoot();
	}
}
