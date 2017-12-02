package sam.apps.jbook_reader.datamaneger;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream.Builder;

import javafx.scene.control.TreeItem;

class Entry extends TreeItem<String> { 
	private String content;
	private long lastModified;
	private transient char[] chars;
	private transient boolean modified;

	Entry(String title, String content, long lastModified) {
		super(title);
		this.content = content;
		this.lastModified = lastModified;
	}
	long getLastModified() {
		return lastModified;
	}
	void updateLastModified() {
		this.lastModified = System.currentTimeMillis();
	}
	String getTitle() { return getValue(); }
	boolean setTitle(String title) {
		if(updated(title, getTitle())) {
			updateLastModified();
			setValue(title);
		}
		return modified;
	}
	boolean updated(String s1, String s2) {
		return modified = modified || !Objects.equals(s1, s2);
	}
	public boolean isModified() {
		return modified;
	}
	String getContent() { return content; }
	boolean setContent(String content) {
		if(updated(content, this.content)) {
			updateLastModified();
			this.content = content;
		}
		return modified;
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
	Entry addChild(String title, String content, long lastmodified, Entry relativeTo) {
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
}