package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.io.infile.TextInFile;
import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.noter.dao.ModBitSet;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;
import sam.noter.dao.zip.RootEntryZFactory.Meta;

class RootEntryZ extends EntryZ implements IRootEntry {
	private static final Logger logger = LogManager.getLogger(RootEntryZ.class);

	private final RootEntryZFactory root;
	private ArrayWrap<EntryZ> entries;
	private ModBitSet mods = new ModBitSet();
	private RootEntryZFactory factory;
	private Path source;
	private String title;
	private boolean modified;
	public final Meta meta;

	public RootEntryZ(Meta meta, Path source, RootEntryZFactory root) throws Exception {
		super(null, -1, -1, null);
		this.meta = meta;
		this.root = root;
		this.source = source;
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
		root.close(this); 
	}
	@Override
	public Path getJbookPath() {
		return source;
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
		
		if(this.source == null) {
			entries = null;
			title = null;
		} else {
			entries = root.getEntries(this);
			this.title = source.getFileName().toString();
		}
	}
	@Override
	public void save(Path file) throws Exception {
		root.save(this, file);
		if(source.equals(file))
			mods.clear();
	}
	private EntryZ cast(IEntry e) {
		return (EntryZ)e;
	}

	@Override
	public IEntry addChild(String title, IEntry parent, int index) {
		EntryZ entry = new EntryZ(this, entries.nextId(), title, true);
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
			.collect(Collectors.groupingBy(IEntry::parent))
			.forEach((p, children) -> {
				EntryZ e = cast(p); 
				e.children().removeAll(children);
				e.setModified(ModifiedField.CHILDREN, true);
			} );

			parent.children().addAll(index, childrenToMove);
			parent.setModified(ModifiedField.CHILDREN, true);
			return childrenToMove;
		}

		childrenToMove.stream()
		.peek(e -> checkIfSame(parent, e))
		.collect(Collectors.groupingBy(e -> castNonNull(e).getRoot()))
		.forEach((root, children) -> children.forEach(root::remove));

		childrenToMove.stream()
		.collect(Collectors.groupingBy(IEntry::parent))
		.forEach((p, childrn) -> {
			EntryZ e = castNonNull(p); 
			e.children().removeAll(childrn);
			e.setModified(ModifiedField.CHILDREN, true);
		});

		List<IEntry> list = changeRoot(childrenToMove);

		childrenToMove = null;
		EntryZ e = cast(newParent); 
		e.children().addAll(index, list);
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

		EntryZ result = new EntryZ(this, nextId++, d);
		root.remove(d);
		put(result);
		
		TextInFile f;
		f.rea

		if(d.getChildren().isEmpty()) 
			return result;

		result.children().addAll(changeRoot(d.getChildren()));
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
		p.children().add(index, c);
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
		EntryZ p = castNonNull(d.parent()); 
		p.children().remove(d);
		p.setModified(ModifiedField.CHILDREN, true);

	}
	@Override
	public void walk(Consumer<IEntry> consumer) {
		for (EntryZ e : old_entries) {
			if(e != null)
				consumer.accept(e);
		}
		for (EntryZ e : new_entries) {
			if(e != null)
				consumer.accept(e);
		}
	}
	@Override
	public String getTitle() {
		return title;
	}
	@Override
	public IEntry getEntryById(int id) {
		if(id < old_entries.length)
			return old_entries[id];
		else if(id - old_entries.length < new_entries.length)
			return new_entries[id - old_entries.length];
		else
			return null;
	}
	public String readContent(EntryZ e) throws IOException {
		return root.readContent(this, e);
	}

}
