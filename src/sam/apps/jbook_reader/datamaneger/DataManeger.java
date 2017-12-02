package sam.apps.jbook_reader.datamaneger;

import java.nio.file.Path;
import java.util.Arrays;
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
	private boolean permanentModified = false;
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
		permanentModified = false;
		modified = false;
	}
	public TreeItem<String> getRootItem() {
		return rootItem;
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
		if(!permanentModified && !modified && jbookPath != null)
			return;

		new EntryEncoder().encode(rootItem.getChildren().stream().map(i -> (Entry)i).collect(Collectors.toList()), path);

		permanentModified = false;
		modified = false;
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
		permanentModified = true;
		return entry;
	}
	private Entry getParent(TreeItem<String> selectedItem) {
		return selectedItem == null || selectedItem.getParent() == rootItem ? null : (Entry)selectedItem.getParent();
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

		for (TreeItem<String> e : rootItem.getChildren())
			((Entry)e).walk(builder);

		return builder.build();
	}
	public Path getJbookPath() {
		return jbookPath;
	}
	public String getContent(TreeItem<String> n) {
		return n == null ? null : ((Entry)n).getContent();
	}
	public String getTitle(TreeItem<String> item) {
		return item == null ? null : ((Entry)item).getTitle();
	}
	public long getLastModifiedTime(TreeItem<String> item) {
		return item == null ? 0 : ((Entry)item).getLastModified();
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

	public void setPermanentModified() {
		this.permanentModified = true;
	}
}
