package sam.noter.dao;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream.Builder;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public abstract class Entry extends TreeItem<String> {
	protected String content;
	protected long lastModified = -1;
	protected boolean childrenLoaded;

	protected Supplier<String> contentProxy;

	protected Entry() {}

	protected Entry(String title, String content, long lastModified) {
		super(title);
		this.content = content;
		this.lastModified = lastModified;
	}
	public void setContentProxy(Supplier<String> contentProxy) {
		this.contentProxy = contentProxy;
	}

	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		if(!childrenLoaded)
			loadChildren(super.getChildren());
		childrenLoaded = true;
		return super.getChildren();
	}
	public abstract void setContent(String content) ;
	
	protected abstract void loadChildren(List<TreeItem<String>> sink);
	protected abstract Entry newEntry(String title, String content, long lastModified);
	public abstract boolean remove(TreeItem<String> t);
	public abstract boolean add(Entry child) ;
	public abstract void add(int index, Entry child);
	public abstract void addAll(int index, List<TreeItem<String>> list);
	public abstract void addAll(List<TreeItem<String>> list);
	public abstract void resetRootEntry();
	
	public long getLastModified() {
		return lastModified;
	}
	public String getTitle() { return getValue(); }
	public abstract void setTitle(String title) ;
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
	
	protected void addAll(int index, List<TreeItem<String>> from, List<TreeItem<String>> to) {
		if(index < 0)
			to.addAll(0, from);
		else if(index >= to.size())
			to.addAll(from);
		else
			to.addAll(index, from);
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
}