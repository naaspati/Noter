package sam.noter.dao.zip;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.TreeItem;
import sam.noter.dao.Entry;

class EntryZ extends Entry {
	private final RootEntryZ root;
	private boolean contentLoaded;
	
	public EntryZ(RootEntryZ dir, int id, long lastModified, String title) {
		super(id, title, null, lastModified);
		this.root = dir;
	}

	public EntryZ(RootEntryZ dir, int id, String title, boolean isNew) {
		super(id, title);
		this.root = dir;
		if(isNew) {
			lastModified = System.currentTimeMillis();
			titleM = true;
			contentM = true;
			childrenM = true;
			contentLoaded = true;
		}
	}
	
	public EntryZ(int id, Entry from) {
		super(id, from);
		root = null;
		contentLoaded = true;
	}

	@Override
	public String getContent() {
		if(!contentLoaded) {
			contentLoaded = true;
			try {
				content = root.getContent(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return super.getContent();
	}

	public void setItems(List<EntryZ> items) {
		this.items.setAll(items);
	}
	@Override
	protected void clearModified() {
		super.clearModified();
	}
	public RootEntryZ getRoot() {
		return root;
	}

	@Override
	public void setLastModified(long lastModified) {
		super.setLastModified(lastModified);
	}
	@Override protected void addAll(List child, int index) { super.addAll(child, index); }
	@Override protected void add(Entry child, int index) { super.add(child, index); }
	@Override protected void modifiableChildren(Consumer<List<TreeItem<String>>> modify) { super.modifiableChildren(modify); }
}
