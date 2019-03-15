package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.noter.Utils;
import sam.noter.dao.ModBitSet;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;
import sam.noter.dao.zip.RootEntryZFactory.Meta;

class RootEntryZ extends EntryZ implements IRootEntry {
	private static final Logger logger = Utils.logger(RootEntryZ.class);
	private static final boolean DEBUG  = logger.isDebugEnabled();

	private ArrayWrap<EntryZ> entries;
	private ModBitSet mods = new ModBitSet();
	private Cache cache;
	private String title;
	private boolean modified;
	
	public RootEntryZ(Cache cache) throws Exception {
		super(null, null, -1, -1, null);
		this.cache = cache;
		reload();
	}
	@Override
	protected Logger logger() {
		return logger;
	}
	boolean isModified(int id, ModifiedField field) {
		Checker.assertTrue(id >= 0);
		return mods.isModified(id, field);
	}
	void setModified(int id, ModifiedField field, boolean value) {
		Checker.assertTrue(id >= 0); 
		mods.setModified(id, field, value);
	}
	@Override
	protected void setModified(ModifiedField field, boolean value) {
		if(field == ModifiedField.CHILDREN)
			modified = value;
	}
	@Override
	public boolean isModified(ModifiedField field) {
		return modified;
	}
	@Override
	public int modCount() {
		return mods.modCount();
	}

	@Override 
	public void close() throws Exception {
		cache.close(); 
	}
	@Override
	public Path getJbookPath() {
		return cache.source();
	}
	@Override
	public void setJbookPath(Path path) {
		Junk.notYetImplemented();
		/* FIXME
		 * Objects.requireNonNull(path);
		cacheDir.setSourceFile(path);
		this.title = path.getFileName().toString();
		 */
	}

	@Override
	public void reload() throws Exception {
		if(entries != null)
			entries.clear();
		
		if(cache.source() == null) {
			entries = null;
			title = null;
		} else {
			entries = cache.getEntries(this);
			this.title = cache.source().getFileName().toString();
		}
	}
	@Override
	public void save(Path file) throws Exception {
		cache.save(file);
		if(cache.source().equals(file))
			mods.clear();
	}
	private EntryZ cast(IEntry e) {
		return (EntryZ)e;
	}

	@Override
	public IEntry addChild(String title, IEntry parent, int index) {
		EntryZ entry = new EntryZ(this, (EntryZ)parent, entries.nextId(), title, true);
		put(entry);

		addChild(entry, parent, index);
		return entry;
	}

	@Override
	public List<IEntry> moveChild(List<IEntry> childrenToMove, IEntry newParent, int index) {
		if(Checker.isEmpty(childrenToMove)) return Collections.emptyList();

		EntryZ parent = check(newParent);

		if(childrenToMove.stream().allMatch(c -> castNonNull(c).getRoot() == this)) {
			childrenToMove.stream()
			.peek(e -> checkIfSame(parent, e))
			.collect(Collectors.groupingBy(IEntry::getParent))
			.forEach((p, children) -> {
				EntryZ e = cast(p); 
				e.getChildren().removeAll(children);
				e.setModified(ModifiedField.CHILDREN, true);
			} );

			parent.getChildren().addAll(index, childrenToMove);
			parent.setModified(ModifiedField.CHILDREN, true);
			return childrenToMove;
		}

		childrenToMove.stream()
		.peek(e -> checkIfSame(parent, e))
		.collect(Collectors.groupingBy(e -> castNonNull(e).getRoot()))
		.forEach((root, children) -> children.forEach(root::remove));

		childrenToMove.stream()
		.collect(Collectors.groupingBy(IEntry::getParent))
		.forEach((p, childrn) -> {
			EntryZ e = castNonNull(p); 
			e.getChildren().removeAll(childrn);
			e.setModified(ModifiedField.CHILDREN, true);
		});

		List<IEntry> list = changeRoot(childrenToMove);

		childrenToMove = null;
		EntryZ e = cast(newParent); 
		e.getChildren().addAll(index, list);
		e.setModified(ModifiedField.CHILDREN, true);
		return list;
	}

	private void put(EntryZ entry) {
		entries.set(entry.getId(), entry);
	}
	private void remove(IEntry e) {
		entries.set(e.getId(), null);
	}
	private EntryZ changeRoot(EntryZ d) {
		RootEntryZ root = cast(d).getRoot(); 
		if(root == this) return d;

		EntryZ result = new EntryZ(this, entries.nextId(), d);
		root.remove(d);
		put(result);

		if(d.getChildren().isEmpty()) 
			return result;

		result.getChildren().addAll(changeRoot(d.getChildren()));
		result.setModified(ModifiedField.CHILDREN, true);
		return result;
	}
	private List<IEntry> changeRoot(List<?> children) {
		return children.stream()
				.map(t -> changeRoot(castNonNull(t)))
				.peek(this::put)
				.collect(Collectors.toList());
	}

	private void checkIfSame(IEntry parent, IEntry child) {
		if(parent == child)
			throw new IllegalArgumentException("child and parent are same IEntry");
	}

	@Override
	public void addChild(IEntry child, IEntry parent, int index) {
		EntryZ c = check(child);
		EntryZ p = check(parent);
		checkIfSame(p, c);

		put(c);
		p.getChildren().add(index, c);
		p.setModified(ModifiedField.CHILDREN, true);
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
	public void removeFromParent(IEntry child) {
		EntryZ d = check(child);
		remove(d);
		EntryZ p = castNonNull(d.getParent()); 
		p.getChildren().remove(d);
		p.setModified(ModifiedField.CHILDREN, true);

	}
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void forEachFlattened(Consumer<IEntry> consumer) {
		Consumer c = consumer;
		entries.forEach(c);
	}
	
	@Override
	public String getTitle() {
		return title;
	}
	@Override
	public IEntry getEntryById(int id) {
		return entries.get(id);
	}
	public String readContent(EntryZ e) throws IOException {
		return cache.readContent(e);
	}
	public List<IEntry> getChildren(EntryZ entryZ) {
		// TODO Auto-generated method stub
		return null;
	}

}
