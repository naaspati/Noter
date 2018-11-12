package sam.noter.dao.zip;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
			if(content == null) 
				content = Util.get(() -> root.getContent(this), content);
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
	@Override protected void addAll(@SuppressWarnings("rawtypes") List child, int index) { super.addAll(child, index); }
	@Override protected void add(Entry child, int index) { super.add(child, index); }
	@Override protected void modifiableChildren(Consumer<List<TreeItem<String>>> modify) { super.modifiableChildren(modify); }

	public void write(DataOutputStream dos) throws IOException {
		dos.writeInt(id);
		dos.writeLong(lastModified);
		String s = getTitle();
		dos.writeUTF(s == null ? "" : s);
		
		dos.writeInt(items.size());
		if(items.isEmpty()) return;
		
		for (TreeItem<String> t : items)
			((EntryZ)t).write(dos);
	}
	public static EntryZ read(DataInputStream dis, RootEntryZ root) throws IOException {
		EntryZ e = new EntryZ(root, dis.readInt(), dis.readLong(), dis.readUTF());
		int size = dis.readInt();
		if(size != 0)  {
			for (int i = 0; i < size; i++) 
				e.items.add(read(dis, root));	
		}
		return e;
	}
}
