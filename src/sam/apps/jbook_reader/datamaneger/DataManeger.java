package sam.apps.jbook_reader.datamaneger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.scene.control.TreeItem;

/**
 * DataManeger and View Controller
 * 
 * @author Sameer
 *
 */
public class DataManeger {

	private boolean modified = false;
	private Path jbookPath;
	private TreeItem<String> rootItem;

	final Map<TreeItem<String>, Entry> map = new HashMap<>();
	private final List<Entry> rootEntries;

	protected DataManeger() {
		rootEntries = new ArrayList<>();
	}

	protected DataManeger(Path jbookPath) throws Exception {
		rootEntries = new EntryDecoder().decode(jbookPath);
		rootEntries.forEach(e -> e.setManeger(this));
		this.jbookPath = jbookPath;
	}
	public void reload() throws Exception {
		if(jbookPath == null)
			return;
		rootEntries.clear();
		rootEntries.addAll(new EntryDecoder().decode(jbookPath));
		rootEntries.forEach(e -> e.setManeger(this));
		fillRootItem();
	}
	public boolean isModified() {
		return modified;
	}
	protected void setModified(boolean m) {
		modified = modified || m;
	}
	public TreeItem<String> getRootItem() {
		if(rootItem != null)
			return rootItem;

		rootItem = new TreeItem<>("");
		fillRootItem();
		return rootItem;
	}

	private void fillRootItem() {
		rootItem.getChildren().clear();
		rootEntries.stream().map(Entry::getItem).collect(Collectors.toCollection(rootItem::getChildren));
	}

	public void save() throws Exception {
		save(jbookPath);
	}
	public void save(Path path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		new EntryEncoder().encode(rootEntries, path);
		modified = false;
		setModified(false);
	}

	public TreeItem<String> add(TreeItem<String> parent, String title) {
		Entry e = new Entry(title, this);

		Entry pe = map.get(parent);
		if(pe == null) {
			map.put(e.getItem(), e);
			rootItem.getChildren().add(e.getItem());
			rootEntries.add(e);
		}
		else
			pe.addEntry(e);

		setModified(true);
		return e.getItem();
	}
	public void remove(List<TreeItem<String>> items) {
		new ArrayList<>(items).forEach(t -> {
			Entry e = map.get(t);
			Entry parent = e.getParent();
			if(parent == null) {
				rootEntries.remove(e);
				rootItem.getChildren().remove(e.getItem());
			}
			else
				parent.remove(e);
		});
	}

	@SuppressWarnings("unchecked")
	public TreeItem<String>[] search(String n) {
		if(n == null || n.isEmpty())
			return null;

		char[] chars =  n.toLowerCase().toCharArray();

		return map.values().stream()
				.filter(e -> e.testTitle(chars))
				.map(Entry::getItem)
				.toArray(TreeItem[]::new);
	}
	public Path getJbookPath() {
		return jbookPath;
	}
	public void setExpanded(boolean b) {
		map.keySet().forEach(t -> t.setExpanded(b));
	}
	public void setTitle(TreeItem<String> item, String newTitle) {
		setModified(map.get(item).setTitle(newTitle));
	}
	public String getContent(TreeItem<String> n) {
		return Optional.ofNullable(n)
				.map(map::get)
				.map(Entry::getContent)
				.orElse("");
	}
	public void setContent(TreeItem<String> item, String content) {
		Entry e = map.get(item);
		if(e != null)
			setModified(e.setContent(content));
	}

	public void setJbookPath(Path path) {
		jbookPath = path;
		
	}
}
