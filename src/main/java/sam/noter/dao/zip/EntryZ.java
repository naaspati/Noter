package sam.noter.dao.zip;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.noter.dao.Entry;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;

class EntryZ extends Entry {
	private static final Logger logger = LogManager.getLogger(EntryZ.class); 
	private final RootEntryZ root;

	public EntryZ(RootEntryZ dir, int id, long lastModified, String title) {
		super(id, title, null, lastModified);
		this.root = dir;
	}

	public EntryZ(RootEntryZ dir, int id, String title, boolean isNew) {
		super(id, title);
		this.root = dir;
		if(isNew) {
			lastModified = System.currentTimeMillis();
			setModified(ModifiedField.ALL, true);
		}
	}

	public EntryZ(RootEntryZ root, int id, Entry from) {
		super(id, from);
		this.root = root;
	}
	@Override
	public String getContent() {
		if(content == null)
			return content = root.readContent(this);
		return content;
	}
	
	public RootEntryZ getRoot() {
		return root;
	}
	
	@Override
	public boolean isModified(ModifiedField field) {
		return root.isModified(id, field);
	}
	@Override
	protected void setModified(ModifiedField field, boolean value) {
		root.setModified(id, field, value);
	}
	@Override
	public IRootEntry root() {
		return root;
	}
	
	@Override
	public IEntry getParent() {
		return null;
	}

	@Override
	protected Logger logger() {
		return logger;
	}

	List<IEntry> children() {
		return children;
	}
}
