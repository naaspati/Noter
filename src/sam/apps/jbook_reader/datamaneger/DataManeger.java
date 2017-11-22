package sam.apps.jbook_reader.datamaneger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
	private final List<Entry> entries;

	public DataManeger() {
		entries = new ArrayList<>();
	}

	protected DataManeger(Path jbookPath) throws Exception {
		entries = new EntryDecoder().decode(jbookPath);
		this.jbookPath = jbookPath;
	}
	public List<? extends TreeItem<String>> reload() throws Exception {
		if(jbookPath == null)
			return null;

		entries.clear();
		entries.addAll(new EntryDecoder().decode(jbookPath));
		return getItems();
	}
	public List<? extends TreeItem<String>> getItems() {
		return Collections.unmodifiableList(entries);
	}
	public boolean isModified() {
		return modified;
	}
	protected void setModified(boolean m) {
		modified = modified || m;
	}
	public void save() throws Exception {
		save(jbookPath);
	}
	public void save(Path path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		new EntryEncoder().encode(entries, path);

		modified = false;
		setModified(modified);
	}

	public TreeItem<String> add(TreeItem<String> selectedItem, TreeItem<String> rootItem, String title, boolean addChild) {
		Entry entry;
		Entry parent = addChild ? (Entry)selectedItem : getParent(selectedItem);

		if(parent != null)
			entry = parent.addChild(title, null, System.currentTimeMillis(), (Entry)selectedItem);
		else {
			entry = new Entry(title, null, System.currentTimeMillis());
			rootItem.getChildren().add(entry);
			entries.add(entry);
		}
		setModified(true);
		return entry;
	}
	private Entry getParent(TreeItem<String> selectedItem) {
		return selectedItem == null || entries.contains(selectedItem) ? null : (Entry)selectedItem.getParent();
	}
	public void remove(List<TreeItem<String>> items, TreeItem<String> rootItem) {
		if(items == null || items.isEmpty())
			return;

		new ArrayList<>(items).forEach(t -> {
			Entry parent = getParent(t);
			if(parent == null) {
				entries.remove(t);
				rootItem.getChildren().remove(t);
			}
			else
				parent.remove(t);
		});
		setModified(true);
	}
	@SuppressWarnings("unchecked")
	public TreeItem<String>[] search(String title, String content) {
		if((title == null || title.isEmpty()) && (content == null || content.isEmpty()))
			return null;

		char[] chars =  title == null ? null : title.toLowerCase().toCharArray();
		if(chars != null)
			Arrays.sort(chars);

		return walk()
				.filter(e -> e.test(chars, content))
				.toArray(TreeItem[]::new);
	}

	public Stream<? extends TreeItem<String>> walk(TreeItem<String> nnew) {
		Stream.Builder<Entry> builder = Stream.builder();
		((Entry)nnew).walk(builder);
		return builder.build();
	}
	public Stream<Entry> walk() {
		Stream.Builder<Entry> builder = Stream.builder();

		for (Entry e : entries)
			e.walk(builder);

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
	public String getTitle(TreeItem<String> item) {
		return item == null ? null : ((Entry)item).getTitle();
	}	
	public void setTitle(TreeItem<String> item, String title) {
		if(item != null)
			setModified(((Entry)item).setTitle(title));
	}	
	public void setContent(TreeItem<String> item, String content) {
		if(item != null)
			setModified(((Entry)item).setContent(content));
	}
	public void setJbookPath(Path path) {
		jbookPath = path;

	}
}
