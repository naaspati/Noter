package sam.noter.dao.dom;

import java.util.List;

import org.slf4j.Logger;

import sam.nopkg.Junk;
import sam.noter.Utils;
import sam.noter.dao.Entry;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.dom.DOMLoader.DomEntryInit;

class DOMEntry extends Entry {
	private static final Logger logger = Utils.logger(DOMEntry.class);
	
	private final DomEntryInit dom;
	protected List<IEntry> children;
	
	protected DOMEntry() {
		super(RootDOMEntry.ROOT_ENTRY_ID);
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
	
	@Override
	public long getLastModified() {
		if(lastModified == -1)
			lastModified = dom.lastModified();
		return lastModified;
	}
	
	@Override
	public String getContent() {
		if(content == null)
			content = dom.content();

		return content;
	}

	@Override
	public boolean isModified(ModifiedField field) {
		return dom.isModified(field);
	}
	@Override
	protected void setModified(ModifiedField field, boolean value) {
		dom.setModified(field, value);
	}

	@Override
	public RootDOMEntry root() {
		return dom.getRoot();
	}
	
	@Override
	protected Logger logger() {
		return logger;
	}
	@Override
	public List<IEntry> getChildren() {
		return children;
	}

	@Override
	public IEntry getParent() {
		// TODO Auto-generated method stub
		return Junk.notYetImplemented();
	}
}
