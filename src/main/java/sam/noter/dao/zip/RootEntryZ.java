package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import sam.myutils.Checker;
import sam.noter.dao.Entry;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.RootEntry;

class RootEntryZ extends EntryZ implements RootEntry {
	private CacheDir cacheDir;
	private Runnable onModified;
	private HashMap<Integer, Entry> entries = new HashMap<>();
	private boolean disableNotify;

	public RootEntryZ(CacheDir cacheDir) throws Exception {
		super(null, ROOT_ENTRY_ID, "ROOT", false);
		this.cacheDir = cacheDir;
		reload();
		if(cacheDir.getSourceFile() != null)
			setValue(cacheDir.getSourceFile().getFileName().toString());
	}

	@Override public void close() throws Exception {
		cacheDir.close(this); 
	}

	@Override
	public Path getJbookPath() {
		return cacheDir == null ? null : cacheDir.getSourceFile() == null ? null : cacheDir.getSourceFile();
	}
	@Override
	public void setJbookPath(Path path) {
		Objects.requireNonNull(path);
		cacheDir.setSourceFile(path);
		setValue(path.getFileName().toString());
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
		disableNotify = true;
		cacheDir.loadEntries(this);
		disableNotify = false;
		onModified();
	}
	private void put(Entry e) {
		entries.put(e.getId(), e);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override //TODO
	private void setItems(List items) {
		this.items.setAll(items);
		entries = new HashMap<>(Checker.isEmpty(entries) ? 50 : entries.size()+10);
		entries.clear();
		walk(this::put);
		childrenM = false;
		int n = cacheDir.getSelectedItem();
		selecteditem = n < 0 ? null : entries.get(n);
		onModified();
	}
	@Override
	public void save(Path file) throws Exception {
		cacheDir.save(this, file);
		childrenM = false;
		walk(w -> cast(w).clearModified());
		onModified();
	}
	private EntryZ cast(Entry e) {
		return (EntryZ)e;
	}
	@Override
	public void setOnModified(Runnable onModified) {
		this.onModified = onModified;
	}
	private void onModified() {
		if(!disableNotify && onModified != null) onModified.run();
	}
	@Override
	protected void childModified(ModifiedField field, Entry child, Entry modifiedEntry) {
		childrenM = true;
		onModified();
	}
	@Override protected void notifyParent(ModifiedField field) { }

	@Override
	public Collection<Entry> getAllEntries() {
		return Collections.unmodifiableCollection(entries.values());
	}

	@Override
	public Entry addChild(String title, Entry parent, int index) {
		EntryZ e = cacheDir.newEntry(title, this);
		addChild(e, parent, index);
		return e;
	}

	@Override
	public List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index) {
		if(Checker.isEmpty(childrenToMove)) return Collections.emptyList();

		EntryZ parent = check(newParent);

		if(childrenToMove.stream().allMatch(c -> castNonNull(c).getRoot() == this)) {
			childrenToMove.stream()
			.peek(e -> checkIfSame(parent, e))
			.collect(Collectors.groupingBy(Entry::parent))
			.forEach((p, children) -> cast(p).modifiableChildren(l -> l.removeAll(children)));

			parent.addAll(childrenToMove, index);
			return childrenToMove;
		}

		childrenToMove.stream()
		.peek(e -> checkIfSame(parent, e))
		.collect(Collectors.groupingBy(e -> castNonNull(e).getRoot()))
		.forEach((root, children) -> children.forEach(e -> root.entries.remove(e.getId())));

		childrenToMove.stream()
		.collect(Collectors.groupingBy(Entry::parent))
		.forEach((p, childrn) -> castNonNull(p).modifiableChildren(list -> list.removeAll(childrn)));

		List<Entry> list = changeRoot(childrenToMove);

		childrenToMove = null;
		cast(newParent).addAll(list, index);
		return list;
	}

	private EntryZ changeRoot(EntryZ d) {
		RootEntryZ root = cast(d).getRoot(); 
		if(root == this) return d;

		EntryZ result = cacheDir.newEntry(d, this);
		root.entries.remove(d.getId());

		if(d.getChildren().isEmpty()) return result;
		result.modifiableChildren(list -> list.addAll(changeRoot(d.getChildren())));
		return result;
	}
	private List<Entry> changeRoot(List<?> children) {
		return children.stream()
				.map(t -> changeRoot(castNonNull(t)))
				.peek(this::put)
				.collect(Collectors.toList());
	}

	private void checkIfSame(Entry parent, Entry child) {
		if(parent == child)
			throw new IllegalArgumentException("child and parent are same Entry");
	}

	@Override
	public void addChild(Entry child, Entry parent, int index) {
		EntryZ c = check(child);
		EntryZ p = check(parent);
		checkIfSame(p, c);

		put(c);
		Util.hide(() -> cacheDir.restore(c));
		p.add(c, index);
	}
	private EntryZ castNonNull(Object object) {
		return Objects.requireNonNull(cast(object));
	}
	private EntryZ cast(Object object) {
		return (EntryZ) object;
	}

	@Override
	public RootEntryZ getRoot() {
		return this;
	}
	private EntryZ check(Object e) {
		EntryZ d = castNonNull(e);
		if(d.getRoot() != this)
			throw new IllegalStateException(e+"  is not part of current root");

		return d;
	}

	@Override
	public void removeFromParent(Entry child) {
		EntryZ d = check(child);

		Util.hide(() -> cacheDir.remove(d));
		entries.remove(d.getId());
		castNonNull(d.parent()).modifiableChildren(l -> l.remove(d));
	}
	String getContent(EntryZ e) throws IOException {
		return cacheDir.getContent(e);
	}
	public CacheDir getCacheDir() {
		return cacheDir;
	}

	private Entry selecteditem;

	@Override
	public void setSelectedItem(Entry e) {
		this.selecteditem = e;
	}
	@Override
	public Entry getSelectedItem() {
		return selecteditem;
	}

}
