package sam.apps.jbook_reader.datamaneger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import javafx.scene.control.TreeItem;

class Entry { 
	private String title;
	private String content;
	private long lastModified;
	private List<Entry> children;

	private transient TreeItem<String> item;
	private transient Entry parent;
	private transient char[] chars;
	private BiConsumer<TreeItem<String>, Entry> collector;

	public Entry(String title, String content, long lastModified,BiConsumer<TreeItem<String>, Entry> collector, List<Entry> children) {
		this.title = title;
		this.content = content;
		this.lastModified = lastModified;
		this.children = children;
		this.collector = collector;
	}
	public Entry(String title, String content, BiConsumer<TreeItem<String>, Entry> collector) {
		this(title, content, System.currentTimeMillis(), collector, null);
	}
	public Entry(String title, BiConsumer<TreeItem<String>, Entry> collector) {
		this(title, (String)null, collector);
	}
	public long getLastModified() {
		return lastModified;
	}
	public void updateLastModified() {
		this.lastModified = System.currentTimeMillis();
	}
	public List<Entry> getChildren() {
		return children;
	}
	public Entry getParent() { return parent; }
	private void setParent(Entry parent) { 
		this.parent = parent;
	}
	public String getTitle() { return title; }
	public boolean setTitle(String title) {
		if(Objects.equals(title, this.title))
			return false;

		updateLastModified();
		this.title = title;
		return true;
	}
	public String getContent() { return content; }
	public boolean setContent(String content) {
		if(Objects.equals(content, this.content))
			return false;

		updateLastModified();
		this.content = content;
		return true;
	}
	boolean testTitle(char[] against) {
		if(chars == null) {
			chars = title.toLowerCase().toCharArray();
			Arrays.sort(chars);
		}
		for (char c : against) {
			if(Arrays.binarySearch(chars, c) < 0)
				return false;
		}
		return true;
	} 
	public void addEntry(Entry e) {
		if(e == null)
			return;

		if(children == null)
			children = new ArrayList<>();

		e.setParent(this);
		children.add(e);
		item.getChildren().add(e.getItem());
	}
	public void remove(Entry e) {
		children.remove(e);
		item.getChildren().remove(e.getItem());
	}
	public List<Entry> getEntries() {
		return children;
	}
	public TreeItem<String> getItem() {
		if(item == null) {
			item = new TreeItem<String>(title);
			collector.accept(item, this);

			if(children != null) {
				children.stream()
				.peek(e -> e.setParent(this))
				.map(Entry::getItem)
				.forEach(item.getChildren()::add);
			}
		}
		return item;
	}
}