package sam.noter.dao.dom;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.noter.dao.Entry;
import sam.noter.dao.dom.DOMLoader.DomEntryInit;

class DOMEntry extends Entry {
	private final DomEntryInit dom;
	
	public DOMEntry() {
		super(-1);
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
		super(dom == null ? -1 : dom.id(), title);
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
	@Override
	protected void loadChildren(@SuppressWarnings("rawtypes") List sink)  {
		dom.collectChildren(items);
	}

	@Override protected boolean setTitle(String title) { return super.setTitle(title); }
	@Override protected void updateLastmodified() { super.updateLastmodified(); }
	@Override protected void setLastModified(long lastModified) { super.setLastModified(lastModified); }
	@Override protected boolean setContent(String content) { return super.setContent(content); }
	@Override protected ObservableList<TreeItem<String>> getModifiableChildren() { return super.getModifiableChildren(); }
	

}
