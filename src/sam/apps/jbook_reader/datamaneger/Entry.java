package sam.apps.jbook_reader.datamaneger;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream.Builder;

import javafx.scene.control.TreeItem;

class Entry extends TreeItem<String> { 
	private String content;
	private long lastModified;
	private transient char[] chars;

	public Entry(String title, String content, long lastModified) {
		super(title);
		this.content = content;
		this.lastModified = lastModified;
	}

	public long getLastModified() {
		return lastModified;
	}
	public void updateLastModified() {
		this.lastModified = System.currentTimeMillis();
	}
	public String getTitle() { return getValue(); }
	public boolean setTitle(String title) {
		if(Objects.equals(title, getValue()))
			return false;
		
		updateLastModified();
		setValue(title);
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
	Builder<Entry> walk(Builder<Entry> collector) {
		collector.accept(this);

		for (TreeItem<String> t : getChildren())
			((Entry)t).walk(collector);
		
		return collector; 
	}
	boolean test(char[] titleSearch, String contentSearch) {
		if(chars == null && titleSearch != null) {
			chars = getTitle().toLowerCase().toCharArray();
			Arrays.sort(chars);
		}
		boolean b = true;
		if(titleSearch != null) {
			if(chars.length < titleSearch.length)
				b = false;
			for (char c : titleSearch) {
				if(Arrays.binarySearch(chars, c) < 0) {
					b = false;
					break;
				}
			}
			if(b)
				return true;
		}
		
		return content != null && contentSearch != null && content.contains(contentSearch);
	} 
	public Entry addChild(String title, String content, long lastmodified, Entry relativeTo) {
		Entry child = new Entry(title, content, lastmodified);

		if(relativeTo == null)
			getChildren().add(child);
		else {
			int index = getChildren().indexOf(relativeTo);
			index = index < 0 ? getChildren().size() : index + 1;
			getChildren().add(index, child);
		}
		return child;
	}
	public void remove(TreeItem<String> e) {
		getChildren().remove(e);
	}
	public void forEach(Consumer<Entry> c) {
		for (TreeItem<String> t : getChildren())
			c.accept(((Entry)t));
	}
}