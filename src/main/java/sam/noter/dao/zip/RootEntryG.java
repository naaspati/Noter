package sam.noter.dao.zip;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import sam.noter.dao.Entry;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.RootEntry;
import sam.noter.dao.zip.RootEntryGFactory.CacheDir;

class RootEntryG extends EntryG implements RootEntry {
	private CacheDir cacheDir;
	private Runnable onModified;
	private List<Entry> entries;

	public RootEntryG(CacheDir cacheDir) throws Exception {
		super(null, RootEntry.ROOT_ENTRY_ID, "ROOT", false);
		this.cacheDir = cacheDir;
		reload();
	}

	@Override public void close() throws Exception {/* DOES  NOTHING */ }

	@Override
	public File getJbookPath() {
		return cacheDir == null ? null : cacheDir.getSourceFile() == null ? null : cacheDir.getSourceFile().toFile();
	}
	@Override
	public void setJbookPath(File path) {
		Objects.requireNonNull(path);
		cacheDir.setSourceFile(path.toPath());
	}

	@Override
	public boolean isModified() {
		return childrenM;
	}
	void setModified() {
		childrenM = true;
	}

	@Override
	public void reload() throws Exception {
		setItems(cacheDir.loadEntries());
	}
	@Override
	public void setItems(List<EntryG> items) {
		this.items.setAll(items);
		entries = new ArrayList<>(entries == null ? 50 : entries.size());
		walk(entries::add);
		childrenM = false;
		onModified();
	}
	@Override
	public void save(File file) throws Exception {
		cacheDir.save(this, file);
		entries.forEach(e -> cast(e).clearModified());
		childrenM = false;
		onModified();
	}
	private EntryG cast(Entry e) {
		return (EntryG)e;
	}

	@Override
	public void setOnModified(Runnable onModified) {
		this.onModified = onModified;
	}
	private void onModified() {
		if(onModified != null) onModified.run();
	}
	@Override
	public Entry addChild(String title, Entry parent, int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	protected void childModified(ModifiedField field, Entry child, Entry modifiedEntry) {
		childrenM = true;
		onModified();
	}

	@Override
	public Collection<Entry> getAllEntries() {
		return Collections.unmodifiableList(entries);
	}
}
