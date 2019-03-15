package sam.noter.dao.zip;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;

import sam.noter.Utils;
import sam.noter.dao.Entry;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;

class EntryZ extends Entry {
	private static final Logger logger = Utils.logger(EntryZ.class); 
	private final RootEntryZ root;

	public EntryZ(RootEntryZ dir, EntryZ parent, int id, long lastModified, String title) {
		super(id, parent, title, null, lastModified);
		this.root = dir;
	}

	public EntryZ(RootEntryZ dir, EntryZ parent, int id, String title, boolean isNew) {
		super(id, title, parent);
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
		if(content == null) {
			try {
				return content = root.readContent(this);
			} catch (IOException e) {
				logger.error("failed to load content: {}", this, e);
			}
		}
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
	protected Logger logger() {
		return logger;
	}
	@Override
	public java.util.List<IEntry> getChildren() {
		return root.getChildren(this);
	}
}
