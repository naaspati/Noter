package sam.apps.jbook_reader.datamaneger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
	private final TreeItem<String> rootItem = new TreeItem<>();

	public DataManeger() {}
	
	protected DataManeger(Path jbookPath) throws Exception {
		rootItem.getChildren().setAll(new EntryDecoder().decode(jbookPath));
		this.jbookPath = jbookPath;
	}
	public void reload() throws Exception {
		if(jbookPath == null)
			return;
		rootItem.getChildren().setAll(new EntryDecoder().decode(jbookPath));
	}
	public boolean isModified() {
		return modified;
	}
	protected void setModified(boolean m) {
		modified = modified || m;
	}
	public TreeItem<String> getRootItem() {
		return rootItem;
	}
	public void save() throws Exception {
		save(jbookPath);
	}
	public void save(Path path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		new EntryEncoder().encode(
				rootItem.getChildren().stream()
				.map(Entry.class::cast)
				.collect(Collectors.toList()), 
				path);

		modified = false;
		setModified(modified);
	}

	public TreeItem<String> add(TreeItem<String> selectedItem, String title, boolean addChild) {
		Entry entry;
		Entry parent = addChild ? (Entry)selectedItem : getParent(selectedItem); 

		if(parent != null)
			entry = parent.addChild(title, null, System.currentTimeMillis(), (Entry)selectedItem);
		else {
			entry = new Entry(title, null, System.currentTimeMillis());
			rootItem.getChildren().add(entry);
		}
		setModified(true);
		return entry;
	}
	private Entry getParent(TreeItem<String> selectedItem) {
		return selectedItem == null || selectedItem.getParent() == rootItem ? null : (Entry)selectedItem.getParent();
	}
	public void remove(List<TreeItem<String>> items) {
		if(items == null || items.isEmpty())
			return;
		
		new ArrayList<>(items).forEach(t -> {
			Entry parent = getParent(t);
			if(parent == null)
				rootItem.getChildren().remove(t);
			else
				parent.remove(t);
		});
		setModified(true);
	}

	@SuppressWarnings("unchecked")
	public TreeItem<String>[] search(String n) {
		if(n == null || n.isEmpty())
			return null;

		char[] chars =  n.toLowerCase().toCharArray();

		return walk()
				.filter(e -> e.testTitle(chars))
				.toArray(TreeItem[]::new);
	}
	private Stream<Entry> walk() {
		Stream.Builder<Entry> builder = Stream.builder();

		for (TreeItem<String> t : rootItem.getChildren())
			((Entry)t).walk(builder);

		return builder.build();
	}
	public Path getJbookPath() {
		return jbookPath;
	}
	public void setExpanded(boolean b) {
		walk().forEach(t -> t.setExpanded(b));
	}
	public String getContent(TreeItem<String> n) {
		return n == null ? null : ((Entry)n).getContent();
	}
	public void setContent(TreeItem<String> item, String content) {
		if(item != null)
			setModified(((Entry)item).setContent(content));
	}

	public void setJbookPath(Path path) {
		jbookPath = path;

	}
}
