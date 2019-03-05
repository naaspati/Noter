package sam.noter.dao.dom;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import sam.di.Injector;
import sam.myutils.Checker;
import sam.noter.dao.Entry;
import sam.noter.dao.ModHandler;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;

@SuppressWarnings({"unchecked", "rawtypes"})
class RootDOMEntry extends DOMEntry implements IRootEntry {
	public static final int ROOT_ENTRY_ID = -1;

	private Path jbookPath;
	private Runnable onModified;
	private DOMLoader dom;
	private final HashMap<Integer, IEntry> entryMap = new HashMap<>();
	private final ModHandler mods = new ModHandler(ROOT_ENTRY_ID);
	private final Injector injector;

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
		return mods.isModified(id, field);
	}
	public void setModified(int id, ModifiedField field, boolean value) {
		mods.setModified(id, field, value);
		notifyModified();
	}
	@Override
	public boolean isModified() {
		return !mods.isEmpty();
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
		this.dom = new DOMLoader(jbookPath.toFile(), children, this);
		walk(w -> entryMap.put(w.getId(), w));
		notifyModified();
		mods.clear();
	}
	@Override
	public void setOnModified(Runnable action) {
		this.onModified = action;
	}
	protected void notifyModified() {
		if(onModified != null)
			onModified.run();
	}

	@Override
	public void save(Path path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		dom.save(getChildren(), path.toFile());
		clearModified();
		setJbookPath(path);
		mods.clear();
		notifyModified();
	}

	@Override public Path getJbookPath() { return jbookPath; }
	@Override 
	public void setJbookPath(Path path) { 
		jbookPath = path;
		this.title = jbookPath.getFileName().toString();
	}
	
	@Override
	protected void setModified(ModifiedField field, boolean value) {
		//TODO
	}
	
	@Override public void close() throws Exception {/* does nothing */}

	@Override
	public IEntry addChild(String title, IEntry parent,  int index) {
		if(Checker.isEmptyTrimmed(title))
			throw new IllegalArgumentException("bad title: "+title);

		Entry e = dom.newEntry(title);
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
			.collect(Collectors.groupingBy(IEntry::parent))
			.forEach((p, children) -> p.getChildren().removeAll(children));

			parent.getChildren().addAll(index, childrenToMove);
			return childrenToMove;
		}

		for (IEntry c : childrenToMove) {
			if(c.parent() != null)
				castNonNull(c).root().removeFromParent(c);
		}

		List<IEntry> list = changeRoot(childrenToMove);

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
	private List<IEntry> changeRoot(List<?> list) {
		return list.stream()
				.map(c -> changeRoot(castNonNull(c)))
				.collect(Collectors.toList());
	}
	private DOMEntry changeRoot(DOMEntry d) {
		RootDOMEntry root = d.root(); 
		if(root == this) return d;

		DOMEntry result = dom.newEntry(d);
		root.entryMap.remove(d.getId());

		List list = d.getChildren(); 
		if(list.isEmpty()) return result;
		addAll(result, changeRoot(list), Integer.MAX_VALUE);
		return result;
	}
	@Override
	public Collection<IEntry> getAllEntries() {
		return Collections.unmodifiableCollection(entryMap.values());
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
		d.parent().getChildren().remove(e);
	}
}

