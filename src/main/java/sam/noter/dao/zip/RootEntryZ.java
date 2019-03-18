package sam.noter.dao.zip;

import static sam.noter.dao.ModifiedField.CHILDREN;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;

import sam.collection.Pair;
import sam.io.infile.DataMeta;
import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.noter.Utils;
import sam.noter.dao.ModBitSet;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;

class RootEntryZ extends EntryZ implements IRootEntry {
	private static final Logger logger = Utils.logger(RootEntryZ.class);
	private static final boolean DEBUG  = logger.isDebugEnabled();

	private ArrayWrap<EZ> entries;
	private ModBitSet mods = new ModBitSet();
	private Cache cache;
	private String title;
	private boolean modified;
	private RootEntryZ root = this;
	private final List<EZ> children = new ArrayList<>();

	public RootEntryZ(Cache cache) throws Exception {
		super(0, null);
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
		if(field == CHILDREN)
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
			entries = cache.getEntries(this, entries);
			this.title = cache.source().getFileName().toString();
		}
	}
	@Override
	public void save(Path file) throws Exception {
		cache.save(file);
		if(cache.source().equals(file))
			mods.clear();
	}
	private EZ cast(Object e) {
		return (EZ)e;
	}

	@Override
	public IEntry addChild(String title, IEntry parent, int index) {
		EZ entry = new EZ(entries.nextId(), title, (EZ)parent, true);
		put(entry);

		addChild(entry, parent, index);
		return entry;
	}

	@Override
	public List<IEntry> moveChild(List<IEntry> childrenToMove, IEntry newParent, int index) {
		if(Checker.isEmpty(childrenToMove)) 
			return Collections.emptyList();

		EZ parent = check(newParent);
		childrenToMove.forEach(c -> {
			if(c == null)
				throw new NullPointerException();

			ensureNotSame(parent, c);
			ensureNotRoot(c);
		});

		List<EZ> list = parent.getModifiableChildren();
		List<Pair<EZ, EZ>> transferQueue = new ArrayList<>();		

		for (IEntry t : childrenToMove) {
			list.add(move(t, parent, transferQueue));

			if(t.getClass() == EZ.class)
				cast(t).getParent().remove(t);
			else
				t.getParent().getChildren().remove(t);
		}

		if(!transferQueue.isEmpty()) {
			RootEntryZ root = transferQueue.get(0).key.root();
			if(transferQueue.stream().allMatch(e -> e.key.root() == root)) 
				transfer(root, transferQueue);
			else {
				IdentityHashMap<RootEntryZ, List<Pair<EZ, EZ>>> map = transferQueue.stream().collect(Collectors.groupingBy(p -> p.key.root(), IdentityHashMap::new, Collectors.toList()));
				map.forEach(this::transfer);
			}
		}

		/* FIXME
		 *



		childrenToMove.stream()
		.collect(Collectors.groupingBy(IEntry::getParent))
		.forEach((p, childrn) -> {
			EZ e = castNonNull(p); 
			e.getModifiableChildren().removeAll(childrn);
			e.setModified(CHILDREN, true);
		});

		List<IEntry> list = changeRoot(childrenToMove);

		childrenToMove = null;
		EZ e = cast(newParent); 
		e.getModifiableChildren().addAll(index, list);
		e.setModified(CHILDREN, true);
		return list;
		 */

		return Junk.notYetImplemented();
	}

	private void transfer(RootEntryZ root, List<Pair<EZ, EZ>> list) {
		try {
			List<DataMeta> dm = list.stream().map(e -> e.key.meta).filter(d -> !DataMeta.isEmpty(d)).collect(Collectors.toList());
			IdentityHashMap<DataMeta, DataMeta> map = cache.transferFrom(root.cache, dm);
			list.forEach(p -> p.value.meta = map.get(p.key.meta));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	private EZ move(IEntry child, EZ parent, List<Pair<EZ, EZ>> transferQueue) {
		if(child.getClass() == EZ.class) {
			EZ src = (EZ) child;
			EZ target = new EZ(parent, entries.nextId(), src, transferQueue);

			List<EZ> list = src.children;
			src.children = null;

			if(Checker.isNotEmpty(list))
				list.replaceAll(c -> move(c, target, transferQueue));

			src.root().remove(src);
			target.children = list;
			return target;
		} else {
			EZ e2 = new EZ(parent, entries.nextId(), child);
			Collection<? extends IEntry> col = child.getChildren(); 

			if(Checker.isNotEmpty(col)) {
				ArrayList<EZ> list = new ArrayList<>(col.size());
				col.forEach(c -> list.add(move(c, e2, transferQueue)));
				e2.children = list;
				col.clear();
			}
			return e2;
		}
	}
	private void put(EZ entry) {
		entries.set(entry.getId(), entry);
	}
	private void remove(IEntry e) {
		if(e != null)
			entries.set(e.getId(), null);
	}
	private EZ changeRoot(EZ d) {
		return Junk.notYetImplemented();

		/** FIXME 
		 * 		RootEZ root = cast(d).getRoot(); 
		if(root == this) return d;

		EZ result = new EZ(this, entries.nextId(), d);
		root.remove(d);
		put(result);

		if(d.getModifiableChildren().isEmpty()) 
			return result;

		result.getModifiableChildren().addAll(changeRoot(d.getModifiableChildren()));
		result.setModified(CHILDREN, true);
		return result;
		 */

	}
	private List<IEntry> changeRoot(List<?> children) {
		return children.stream()
				.map(t -> changeRoot(castNonNull(t)))
				.peek(this::put)
				.collect(Collectors.toList());
	}

	private void ensureNotSame(IEntry parent, IEntry child) {
		if(parent == child)
			throw new IllegalArgumentException("child and parent are same IEntry");
	}
	private void ensureNotRoot(IEntry e) {
		if(e instanceof IRootEntry)
			throw new IllegalArgumentException("child cannot be a root");
	}

	@Override
	public void addChild(IEntry child, IEntry parent, int index) {
		EZ c = check(child);
		EZ p = check(parent);
		ensureNotSame(p, c);

		put(c);
		p.getModifiableChildren().add(index, c);
		p.setModified(CHILDREN, true);
	}
	private EZ castNonNull(Object object) {
		if(object == null)
			throw new NullPointerException();
		return cast(object);
	}
	private EZ check(Object e) {
		EZ d = castNonNull(e);
		if(d.root() != this)
			throw new IllegalStateException(e+"  is not part of current root");

		return d;
	}

	@Override
	public void removeFromParent(IEntry child) {
		EZ d = check(child);
		remove(d);
		EZ p = castNonNull(d.getParent()); 
		p.getModifiableChildren().remove(d);
		p.setModified(CHILDREN, true);

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

	@Override public String getContent() { return null; }
	@Override public void setContent(String content) { }
	@Override public IRootEntry root() { return this; }
	@Override public List<? extends IEntry> getChildren() { return children; }
	@Override public IEntry getParent() { return null; }
	@Override protected String readContent() { return null; }

	class EZ extends EntryZ {
		private DataMeta meta;
		private List<EZ> children;
		private final EZ parent;

		public EZ(int id, String title, EZ parent, boolean isNew) {
			super(id, title);
			this.parent = parent;
			this.lastModified = System.currentTimeMillis();

			if(isNew) 
				setModified(ModifiedField.ALL, true);
		}
		public void remove(IEntry t) {
			getModifiableChildren().remove(t);
			root.remove(t);
		}
		public EZ(EZ parent, int id, IEntry from) {
			super(id, from.getTitle());
			this.parent = parent;
			this.lastModified = from.getLastModified();
			this.content = from.getContent();

			setModified(ModifiedField.ALL, true);
		}
		public EZ(EZ parent, int id, EZ from, List<Pair<EZ, EZ>> transferQueue) {
			super(id, from.title);
			this.parent = parent;
			this.lastModified = from.lastModified;

			if(from.content != null) {
				this.content = from.content;
				from.meta = null;
			} else {
				if(root == from.root()) {
					this.meta = from.meta;
					from.meta = null;
				} else if(!DataMeta.isEmpty(from.meta))
					transferQueue.add(new Pair<>(from, this));
			}
			setModified(ModifiedField.ALL, true);
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
		public RootEntryZ root() {
			return root;
		}
		@Override
		public List<EZ> getChildren() {
			return children;
		}
		public List<EZ> getModifiableChildren() {
			if(children != null && children.getClass() == ArrayList.class)
				return children;

			root.setModified(CHILDREN, true);
			return this.children = new ArrayList<>(this.children == null ? Collections.emptyList() : new ArrayList<>(children));
		}
		@Override
		protected String readContent() throws IOException {
			return cache.readContent(meta);
		}
		@Override
		public EZ getParent() {
			return parent;
		}
	}

}
