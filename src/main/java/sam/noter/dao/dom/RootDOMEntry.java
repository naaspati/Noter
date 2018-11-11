package sam.noter.dao.dom;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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

	protected File jbookPath;
	protected Runnable onModified;
	protected DOMLoader dom;
	protected final TreeMap<Integer, Entry> entryMap = new TreeMap<>();

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
		childrenM = false;
		entryMap.clear();
		this.dom = new DOMLoader(jbookPath, items, this);
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
	public void save(File path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		dom.save(getChildren(), path);
		clearModified();
		entryMap.forEach((i, e) -> cast(e).clearModified());
		setJbookPath(path);
		childrenM = false;
		notifyModified();
	}

	@Override public File getJbookPath() { return jbookPath; }
	@Override 
	public void setJbookPath(File path) { 
		jbookPath = path;
		setValue(jbookPath.getName());
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
	private DOMEntry changeRoot(DOMEntry d) {
		RootDOMEntry root = d.getRoot(); 
		if(root == this) return d;

		DOMEntry result = dom.newEntry(d);
		root.entryMap.remove(d.getId());

		if(d.getChildren().isEmpty()) return result;
		addAll(result, d.getChildren().stream().map(t -> changeRoot(castNonNull(t))).collect(Collectors.toList()), Integer.MAX_VALUE);
		return result;
	}
	@Override
	public List<Entry> moveChild(List<Entry> childrenToMove, Entry newParent, int index) {
		if(MyUtilsCheck.isEmpty(childrenToMove)) return Collections.emptyList();

		DOMEntry parent = castCheckRoot(newParent);

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

		List<Entry> list = childrenToMove.stream()
				.map(c -> changeRoot(castNonNull(c)))
				.collect(Collectors.toList());

		childrenToMove = null;
		addAll(newParent, list, index);
		return list;
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
		DOMEntry p = castCheckRoot(parent);
		child.forEach(this::castCheckRoot);
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
	private DOMEntry castCheckRoot(Object e) {
		DOMEntry d = castNonNull(e);
		if(d != this && d.getRoot() != this)
			throw new IllegalStateException(e+"  is not part of current root");

		return d;
	}
	@Override
	public void addChild(Entry child, Entry parent, int index) {
		DOMEntry c = castCheckRoot(child);
		DOMEntry p = castCheckRoot(parent);
		checkIfSame(parent, child);
		p.add(c, index);
	}
	@Override
	public void removeFromParent(Entry e) {
		DOMEntry d = castCheckRoot(e);
		entryMap.remove(d.getId());
		castNonNull(d.parent()).modifiableChildren(l -> l.remove(e));
	}
}

