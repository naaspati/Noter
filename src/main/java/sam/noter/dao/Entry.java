package sam.noter.dao;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream.Builder;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.logging.MyLoggerFactory;

public abstract class Entry extends TreeItem<String> {
	protected boolean titleModified, contentModified, childrenModified;
	protected String content;
	protected long lastModified = -1;
	protected boolean childrenLoaded;
	
	private static final Logger LOGGER = MyLoggerFactory.logger(Entry.class.getSimpleName());

	protected Supplier<String> contentProxy;

	protected Entry() {}

	protected Entry(String title, String content, long lastModified) {
		super(title);
		this.content = content;
		this.lastModified = lastModified;
	}
	public Entry(String title) {
		super(title);
	}

	public void setContentProxy(Supplier<String> contentProxy) {
		this.contentProxy = contentProxy;
	}

	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		if(!childrenLoaded) {
			loadChildren(super.getChildren());
			LOGGER.fine(() -> "CHILDREN LOADED: "+getValue());
		}
		childrenLoaded = true;
		return super.getChildren();
	}
	public void setTitle(String title) {
		if(titleModified || !Objects.equals(title, getTitle())) {
			titleModified = true;
			LOGGER.fine(() -> "TITLE MODIFIED: "+getValue() +" -> "+title);
			setValue(title);
		}
	}
	protected abstract void loadChildren(List<TreeItem<String>> sink);
	protected abstract Entry newEntry(String title, String content, long lastModified);
	protected abstract List<TreeItem<String>> modifiedChildren();
	public abstract void resetRootEntry();
	
	public void addAll(List<TreeItem<String>> list) {
		modifiedChildren().addAll(list);
	}

	public boolean remove(TreeItem<String> t) {
		return modifiedChildren().remove(t);
	}
	public boolean add(Entry child) {
		return modifiedChildren().add(child);
	}
	public void add(int index, Entry child) {
		modifiedChildren().add(index, child);
	}
	public void addAll(int index, List<TreeItem<String>> list) {
		addAll(index, list, modifiedChildren());
	}
	protected void addAll(int index, List<TreeItem<String>> from, List<TreeItem<String>> to) {
		if(index < 0)
			to.addAll(0, from);
		else if(index >= to.size())
			to.addAll(from);
		else
			to.addAll(index, from);
	}
	
	public long getLastModified() {
		return lastModified;
	}
	public String getTitle() { return getValue(); }
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content;
	}
	protected Builder<Entry> walk(Builder<Entry> collector) {
		collector.accept(this);

		for (int i = 0; i < getChildren().size(); i++)
			((Entry)getChildren().get(i)).walk(collector);

		return collector; 
	}
	
	public void setContent(String content) {
		if(contentModified || !Objects.equals(content, this.content)) {
			contentModified = true;
			this.content = content; 
			lastModified = System.currentTimeMillis();
		}
	}
	
	public Entry addChild(String title, Entry relativeTo) {
		Entry child = newEntry(title, null, System.currentTimeMillis());

		if(relativeTo == null)
			add(child);
		else {
			int index = indexOf(relativeTo);
			index = index < 0 ? getChildren().size() : index + 1;
			add(index, child);
		}
		return child;
	}
	public int indexOf(Entry item) {
		return getChildren().indexOf(item);
	}
	public boolean isEmpty() {
		return getChildren().isEmpty();
	}
	protected boolean isAnyModified() {
		return titleModified || contentModified || childrenModified;
	}
}