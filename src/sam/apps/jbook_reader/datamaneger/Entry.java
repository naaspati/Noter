package sam.apps.jbook_reader.datamaneger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javafx.scene.control.TreeItem;

class Entry { 
	private String title;
	private String content;
	private long lastModified;
	private List<Entry> children;

	private transient TreeItem<String> item;
	private transient Entry parent;
	private transient char[] chars;
	private transient DataManeger maneger;

	public Entry(String title, String content, long lastModified, List<Entry> children) {
		this.title = title;
		this.content = content;
		this.lastModified = lastModified;
		this.children = children;
	}
	public Entry(String title, String content) {
		this(title, content, System.currentTimeMillis(), null);
	}
	public Entry(String title, DataManeger maneger) {
		this(title, (String)null);
		this.maneger = maneger;
	}
	public void setManeger(DataManeger maneger) {
		this.maneger = maneger;
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
	public void setParent(Entry parent) { this.parent = parent; }
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
			maneger.map.put(item, this);

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