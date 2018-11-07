package sam.noter.dao.dom;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import sam.myutils.MyUtilsCheck;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;

@SuppressWarnings({"unchecked", "rawtypes"})
class RootDOMEntry extends DOMEntry implements RootEntry {

	protected File jbookPath;
	protected boolean modified; 
	protected Runnable onModified;
	protected DOMLoader dom;
	protected final TreeMap<Integer, Entry> entryMap = new TreeMap<>();
	protected final EnumMap<ModificationType, Set<Entry>> modifications = new EnumMap<>(ModificationType.class);
	protected final Map<Integer, Entry> entryMapunModified = Collections.unmodifiableMap(entryMap);

	public RootDOMEntry() throws ParserConfigurationException {
		super();
		this.dom = new DOMLoader(this);
	}
	public RootDOMEntry(File jbookPath) throws IOException, ParserConfigurationException, SAXException {
		super();
		Objects.requireNonNull(jbookPath, "Path to .jbook cannot be null");
		if(!jbookPath.exists()) throw new FileNotFoundException("File not found: "+jbookPath);
		if(jbookPath.isDirectory()) throw new IOException("Not a File:"+jbookPath);

		setJbookPath(jbookPath);
		reload();
	}

	@Override
	public void reload() throws IOException, ParserConfigurationException, SAXException {
		this.items.clear();

		if(jbookPath == null) {
			this.dom = new DOMLoader(this);
			return;
		}
		modified = false;
		this.dom = new DOMLoader(jbookPath, items, this);
		resetEntriesMap();
		notifyModified();
	}
	private void resetEntriesMap() {
		entryMap.clear();
		walk(w -> entryMap.put(w.getId(), w));
	}
	@Override
	public void setOnModified(Runnable action) {
		this.onModified = action;
	}
	protected void notifyModified() {
		if(onModified != null)
			onModified.run();
	}

	//override default behaviour 
	@Override protected void loadChildren(List sink) {}
	@Override public boolean isModified() { return modified; }

	@Override
	public void save(File path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		dom.save(getChildren(), path);
		setJbookPath(path);
		modified = false;
		notifyModified();
	}

	@Override public File getJbookPath() { return jbookPath; }
	@Override public void setJbookPath(File path) { 
		jbookPath = path;
		setValue(jbookPath.getName());
	}
	@Override public void close() throws Exception {/* does nothing */}

	@Override
	public Entry addChild(String title, Entry parent,  int index) {
		if(MyUtilsCheck.isEmptyTrimmed(title))
			throw new IllegalArgumentException("bad title: "+title);

		Entry e = dom.newEntry(title);
		add(parent, index, e);
		entryMap.put(e.getId(), e);
		return e;
	}

	private void checkIfSame(Entry parent, Entry child) {
		if(parent == child)
			throw new IllegalArgumentException("child and parent are same Entry");
	}
	private DOMEntry changeRoot(DOMEntry d) {
		RootDOMEntry root = d.getRoot(); 
		if(root == this) return d;

		DOMEntry result = dom.newEntry(d);
		root.entryMap.remove(d.getId());
		root.modifications.values()
		.forEach(set -> set.remove(d));

		if(d.getChildren().isEmpty()) return result;
		addAll(result, d.getChildren().stream().map(t -> changeRoot(cast(t))).collect(Collectors.toList()), Integer.MAX_VALUE);
		return result;
	}
	@Override
	public List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index) {
		if(MyUtilsCheck.isEmpty(childrenToMove)) return Collections.emptyList();

		DOMEntry parent = checkRoot(newParent);

		if(childrenToMove.stream().allMatch(this::isThisRoot)) {
			for (Entry e : childrenToMove) 
				checkIfSame(parent, e);

			childrenToMove.forEach(this::removeFromParent);
			addAll(newParent, childrenToMove, index);
			return childrenToMove;
		}


		for (Entry c : childrenToMove) {
			if(c.parent() != null)
				cast(c).getRoot().removeFromParent(c);
		}

		List<Entry> list = childrenToMove.stream()
				.map(c -> changeRoot(cast(c)))
				.collect(Collectors.toList());

		childrenToMove = null;
		addAll(newParent, list, index);
		return list;
	}
	private DOMEntry checkRoot(Entry parent) {
		DOMEntry d = cast(parent);
		if(parent != this && d.getRoot() != this)
			throw new IllegalArgumentException("newParent is not part of current root ");

		return  d;
	}
	@Override
	public String toTreeString() {
		return null;
	}
	@Override
	public Map<Integer, Entry> getEntriesMap() {
		return entryMapunModified;
	}
	@Override
	public void setTitle(Entry entry, String title) {
		if(castCheckRoot(entry).setTitle(title))
			modified(ModificationType.TITLE, entry);
	}
	private void modified(ModificationType type, Entry entry) {
		modifications.computeIfAbsent(type, this::treeset).add(entry);
		modified = true;
	}
	@Override
	public void setContent(Entry entry, String content) {
		if(castCheckRoot(entry).setContent(content))
			modified(ModificationType.CONTENT, entry);
	}

	private static final Comparator<Entry> COMPARATOR = Comparator.comparingInt(e -> e.getId());

	private TreeSet<Entry> treeset(Object ignore){
		return new TreeSet<>(COMPARATOR); 
	}

	private boolean removeFromParent(Entry e){
		DOMEntry d = castCheckRoot(e);
		entryMap.remove(d.getId());
		return cast(d.parent()).getModifiableChildren().remove(e);
	}

	//TODO
	private void addAll(Entry parent, List child, int index) {
		DOMEntry p = castCheckRoot(parent);
		child.forEach(this::castCheckRoot);

		if(index <= 0)
			p.getModifiableChildren().addAll(0, child);
		else if(index >= p.size())
			p.getModifiableChildren().addAll(child);
		else
			p.getModifiableChildren().addAll(index, child);

		modified(ModificationType.CHILDREN, p);
		child.forEach(e -> putEntry(cast(e)));
	}
	private void putEntry(DOMEntry e) {
		entryMap.put(e.getId(), e);
		if(e.dom().isNew())
			modified(ModificationType.NEW, e);
	}
	private void add(Entry parent, int index, Entry child) {
		DOMEntry p = castCheckRoot(parent);
		DOMEntry c = castCheckRoot(child);

		if(index <= 0)
			p.getModifiableChildren().add(0, c);
		else if(index >= p.size())
			p.getModifiableChildren().add(c);
		else
			p.getModifiableChildren().add(index, c);

		modified(ModificationType.CHILDREN, p);
		if(c.dom().isNew()) 
			modified(ModificationType.NEW, c); 
	}
	private DOMEntry cast(Object object) {
		return Objects.requireNonNull((DOMEntry) object);
	}
	private boolean isThisRoot(Entry c) {
		return cast(c).getRoot() == this;
	}
	private DOMEntry castCheckRoot(Object e) {
		DOMEntry d = cast(e);
		if(d.getRoot() != this)
			throw new IllegalStateException(e+"  is not part of current root");

		return d;
	}
}

