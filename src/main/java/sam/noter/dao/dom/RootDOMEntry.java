package sam.noter.dao.dom;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.myutils.MyUtilsCheck;
import sam.noter.dao.Entry;
import sam.noter.dao.ModifiedField;
import sam.noter.dao.RootEntry;

@SuppressWarnings({"unchecked", "rawtypes"})
class RootDOMEntry extends DOMEntry implements RootEntry {

	protected Path jbookPath;
	protected Runnable onModified;
	protected DOMLoader dom;
	protected final TreeMap<Integer, Entry> entryMap = new TreeMap<>();

	public RootDOMEntry() throws ParserConfigurationException {
		super();
		this.dom = new DOMLoader(this);
	}
	public RootDOMEntry(Path jbookPath) throws IOException, ParserConfigurationException, SAXException {
		super();
		Objects.requireNonNull(jbookPath, "Path to .jbook cannot be null");
		if(Files.notExists(jbookPath)) throw new FileNotFoundException("Path not found: "+jbookPath);
		if(Files.isDirectory(jbookPath)) throw new IOException("Not a Path:"+jbookPath);

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
		childrenM = false;
		entryMap.clear();
		this.dom = new DOMLoader(jbookPath.toFile(), items, this);
		walk(w -> entryMap.put(w.getId(), w));
		notifyModified();
		childrenM = false;
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
	public ObservableList<TreeItem<String>> getChildren() {
		return unmodifiable;
	}
	@Override public boolean isModified() { return childrenM; }

	@Override
	public void save(Path path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		dom.save(getChildren(), path.toFile());
		clearModified();
		entryMap.forEach((i, e) -> cast(e).clearModified());
		setJbookPath(path);
		childrenM = false;
		notifyModified();
	}

	@Override public Path getJbookPath() { return jbookPath; }
	@Override 
	public void setJbookPath(Path path) { 
		jbookPath = path;
		setValue(jbookPath.getFileName().toString());
	}
	@Override public void close() throws Exception {/* does nothing */}

	@Override
	public Entry addChild(String title, Entry parent,  int index) {
		if(MyUtilsCheck.isEmptyTrimmed(title))
			throw new IllegalArgumentException("bad title: "+title);

		Entry e = dom.newEntry(title);
		addChild(e, parent, index);
		entryMap.put(e.getId(), e);
		return e;
	}

	private void checkIfSame(Entry parent, Entry child) {
		if(parent == child)
			throw new IllegalArgumentException("child and parent are same Entry");
	}
	@Override
	public List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index) {
		if(MyUtilsCheck.isEmpty(childrenToMove)) return Collections.emptyList();

		DOMEntry parent = check(newParent);

		if(childrenToMove.stream().allMatch(c -> castNonNull(c).getRoot() == this)) {
			childrenToMove.stream()
			.peek(e -> checkIfSame(parent, e))
			.collect(Collectors.groupingBy(Entry::parent))
			.forEach((p, children) -> cast(p).modifiableChildren(l -> l.removeAll(children)));
			
			parent.addAll(childrenToMove, index);
			return childrenToMove;
		}

		for (Entry c : childrenToMove) {
			if(c.parent() != null)
				castNonNull(c).getRoot().removeFromParent(c);
		}

		List<Entry> list = changeRoot(childrenToMove);

		childrenToMove = null;
		addAll(newParent, list, index);
		return list;
	}
	@Override
	RootDOMEntry getRoot() {
		return this;
	}
	private DOMEntry check(Object c) {
		Objects.requireNonNull(c);
		DOMEntry d = (DOMEntry)c;
		if(d.getRoot() != this)
			throw new IllegalStateException(d+"  is not part of current root");
		
		return d;
	}
	private List<Entry> changeRoot(List<?> list) {
		return list.stream()
		.map(c -> changeRoot(castNonNull(c)))
		.collect(Collectors.toList());
	}
	private DOMEntry changeRoot(DOMEntry d) {
		RootDOMEntry root = d.getRoot(); 
		if(root == this) return d;

		DOMEntry result = dom.newEntry(d);
		root.entryMap.remove(d.getId());

		List list = d.getChildren(); 
		if(list.isEmpty()) return result;
		addAll(result, changeRoot(list), Integer.MAX_VALUE);
		return result;
	}
	@Override
	public String toTreeString() {
		return null;
	}
	@Override
	public Collection<Entry> getAllEntries() {
		return Collections.unmodifiableCollection(entryMap.values());
	}
	@Override
	protected void childModified(ModifiedField field, Entry child, Entry modifiedEntry) {
		childrenM = true;
		notifyModified();
	}
	@Override protected void notifyParent(ModifiedField field) { }
	
	private void addAll(Entry parent, List child, int index) {
		DOMEntry p = check(parent);
		child.forEach(this::check);
		p.addAll(child, index);
		
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
	public void addChild(Entry child, Entry parent, int index) {
		DOMEntry c = check(child);
		DOMEntry p = check(parent);
		
		checkIfSame(parent, child);
		p.add(c, index);
	}
	@Override
	public void removeFromParent(Entry e) {
		DOMEntry d = check(e);
		entryMap.remove(d.getId());
		cast(d.parent()).modifiableChildren(l -> l.remove(e));
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

