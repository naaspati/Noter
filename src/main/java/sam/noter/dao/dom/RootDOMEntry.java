package sam.noter.dao.dom;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import sam.di.Injector;
import sam.myutils.Checker;
import sam.noter.dao.Entry;
import sam.noter.dao.ModBitSet;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.VisitResult;
import sam.noter.dao.Walker;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;

@SuppressWarnings({"unchecked", "rawtypes"})
class RootDOMEntry extends DOMEntry implements IRootEntry {
	public static final int ROOT_ENTRY_ID = -1;

	private Path jbookPath;
	private DOMLoader dom;
	private final HashMap<Integer, IEntry> entryMap = new HashMap<>();
	private final ModBitSet mods = new ModBitSet();
	private final Injector injector;

	@Override
	public int modCount() {
		return mods.modCount();
	}
	
	public RootDOMEntry(Injector injector) throws ParserConfigurationException {
		this.injector = injector;
		createDom();
	}
	private void createDom() throws ParserConfigurationException {
		this.dom = injector.instance(DOMLoader.class);
		this.dom.init(this);
	}
	public RootDOMEntry(Injector injector, Path jbookPath) throws IOException, ParserConfigurationException, SAXException {
		this.injector = injector;

		Objects.requireNonNull(jbookPath, "Path to .jbook cannot be null");
		if(Files.notExists(jbookPath)) throw new FileNotFoundException("Path not found: "+jbookPath);
		if(Files.isDirectory(jbookPath)) throw new IOException("Not a Path:"+jbookPath);

		setJbookPath(jbookPath);
		reload();
	}
	public boolean isModified(int id, ModifiedField field) {
		return mods.isModified(id + 1, field);
	}
	public void setModified(int id, ModifiedField field, boolean value) {
		mods.setModified(id + 1, field, value);
	}
	@Override
	public void reload() throws IOException, ParserConfigurationException, SAXException {
		getChildren().clear();

		if(jbookPath == null) {
			createDom();
			return;
		}
		mods.clear();
		entryMap.clear();
		this.dom = new DOMLoader(jbookPath.toFile(), children, this, this);
		walk(Walker.of(w -> entryMap.put(w.getId(), w)));
		mods.clear();
	}

	@Override
	public void save(Path path) throws Exception {
		if(mods.isEmpty() && jbookPath != null)
			return;

		dom.save(getChildren(), path.toFile());
		clearModified();
		setJbookPath(path);
		mods.clear();
	}

	@Override public Path getJbookPath() { return jbookPath; }
	
	public void setJbookPath(Path path) { 
		jbookPath = path;
		this.title = jbookPath.getFileName().toString();
	}
	@Override
	public IEntry addChild(String title, IEntry parent,  int index) {
		if(Checker.isEmptyTrimmed(title))
			throw new IllegalArgumentException("bad title: "+title);

		Entry e = dom.newEntry(title, (DOMEntry) parent);
		addChild(e, parent, index);
		entryMap.put(e.getId(), e);
		return e;
	}

	private void checkIfSame(IEntry parent, IEntry child) {
		if(parent == child)
			throw new IllegalArgumentException("child and parent are same IEntry");
	}
	@Override
	public List<IEntry> moveChild(List<IEntry> childrenToMove, IEntry newParent, int index) {
		if(Checker.isEmpty(childrenToMove)) return Collections.emptyList();

		DOMEntry parent = check(newParent);

		if(childrenToMove.stream().allMatch(c -> castNonNull(c).root() == this)) {
			childrenToMove.stream()
			.peek(e -> checkIfSame(parent, e))
			.collect(Collectors.groupingBy(IEntry::getParent))
			.forEach((p, children) -> p.getChildren().removeAll(children));

			parent.getChildren().addAll(index, childrenToMove);
			return childrenToMove;
		}

		for (IEntry c : childrenToMove) {
			if(c.getParent() != null)
				castNonNull(c).root().removeFromParent(c);
		}

		List<IEntry> list = changeRoot(childrenToMove, (DOMEntry) newParent);

		childrenToMove = null;
		addAll(newParent, list, index);
		return list;
	}
	@Override
	public RootDOMEntry root() {
		return this;
	}
	private DOMEntry check(Object c) {
		Objects.requireNonNull(c);
		DOMEntry d = (DOMEntry)c;
		if(d.root() != this)
			throw new IllegalStateException(d+"  is not part of current root");

		return d;
	}
	private List<IEntry> changeRoot(List<?> list, DOMEntry parent) {
		return list.stream()
				.map(c -> changeRoot(castNonNull(c), parent))
				.collect(Collectors.toList());
	}
	private DOMEntry changeRoot(DOMEntry d, DOMEntry parent) {
		RootDOMEntry root = d.root(); 
		if(root == this) return d;

		DOMEntry result = dom.newEntry(d, parent);
		root.entryMap.remove(d.getId());

		List list = d.getChildren(); 
		if(list.isEmpty()) return result;
		addAll(result, changeRoot(list, result), Integer.MAX_VALUE);
		return result;
	}

	private void addAll(IEntry parent, List child, int index) {
		DOMEntry p = check(parent);
		child.forEach(this::check);
		p.getChildren().addAll(index, child);

		child.forEach(e -> putEntry(castNonNull(e)));
	}
	private void putEntry(DOMEntry e) {
		entryMap.put(e.getId(), e);
	}
	private DOMEntry castNonNull(Object object) {
		return Objects.requireNonNull(cast(object));
	}
	private DOMEntry cast(Object object) {
		return (DOMEntry) object;
	}
	@Override
	public void addChild(IEntry child, IEntry parent, int index) {
		DOMEntry c = check(child);
		DOMEntry p = check(parent);

		checkIfSame(parent, child);
		p.getChildren().add(index, c);
	}
	@Override
	public void removeFromParent(IEntry e) {
		DOMEntry d = check(e);
		entryMap.remove(d.getId());
		d.getParent().getChildren().remove(e);
	}
	
	@Override
	public IEntry getEntryById(int id) {
		IEntry[] res = {null};
		walk(e -> {
			if(e.getId() == id) {
				res[0] = e;
				return VisitResult.TERMINATE;
			}
			Checker.assertIsNull(res[0]);
			return VisitResult.CONTINUE;
		});
		
		return res[0];
	}
	@Override
	public void forEachFlattened(Consumer<IEntry> consumer) {
		entryMap.forEach((s,t) -> consumer.accept(t));
	}
}

