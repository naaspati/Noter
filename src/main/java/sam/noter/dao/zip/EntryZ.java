package sam.noter.dao.zip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.scene.control.TreeItem;
import sam.nopkg.Junk;
import sam.noter.Utils;
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

	public EntryZ(int id, Entry from) {
		super(id, from);
		root = null;
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
		// TODO Auto-generated method stub
		return false;
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
	protected void setModified(ModifiedField field, boolean value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected Logger logger() {
		// TODO Auto-generated method stub
		return null;
	}
}
