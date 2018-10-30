package sam.noter.datamaneger;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream.Builder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public class Entry extends TreeItem<String> {

	private String content0;
	private long lastModified = -1;
	private boolean titleModified, contentModified, childrenModified;
	private Element element;
	private final DataManeger maneger;
	private boolean childrenSet;
	private Supplier<String> contentProxy;

	public static Entry cast(TreeItem<String> item) {
		return (Entry)item;
	}
	Entry(String title, String content, long lastModified, DataManeger maneger) {
		super(title);
		this.content0 = content;
		this.lastModified = lastModified;
		this.maneger = maneger;
	}
	public void setContentProxy(Supplier<String> contentProxy) {
		this.contentProxy = contentProxy;
	}
	Entry(Element element, DataManeger maneger) {
		this.element = element;
		setValue(EntryUtils.getTitle(element));
		this.maneger = maneger;
	}
	
	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		if(!childrenSet) {
			if(element != null)
				super.getChildren().setAll(EntryUtils.getChildren(element, maneger));
			childrenSet = true;
		}
		return super.getChildren();
	}
	Element getElement(Document doc) {
		if(!(element == null || titleModified || contentModified || childrenModified))
			return element;
		
		log();

		if(element == null) {
			element = EntryUtils.createEntry(doc, this);
			contentModified = false;
			titleModified = false;
		}
		if(titleModified) {
			EntryUtils.setTitle(element, getTitle(), doc);
			titleModified = false;
		}
		if(contentModified) {
			EntryUtils.setContent(element, getContent(), doc);
			contentModified = false;
		}
		if(childrenModified) {
			EntryUtils.setChildren(element, doc, getChildren().isEmpty() ? null : getChildren().stream().map(t -> ((Entry)t).getElement(doc)));
			childrenModified = false;
		} else {
			for (int i = 0; i < getChildren().size(); i++)
				((Entry)getChildren().get(i)).getElement(doc);
		}
		return element;
	}
	private void log() {
		if(element == null)
			System.out.println(getTitle() +" ->  new");
		else if(titleModified || 
				contentModified || 
				childrenModified) {

			System.out.println(getTitle()+" -> "+(titleModified ? "title " : "") +
					(contentModified ? "content " : "") +
					(childrenModified ? "children " : ""));			
		}
	}
	public void removeElement() {
		getTitle();
		getContent();
		getChildren();
		titleModified = true; 
		contentModified  = true; 
		childrenModified = true;
		
		getChildren().forEach(e -> ((Entry)e).removeElement());
		element = null;
	}
	public long getLastModified() {
		if(lastModified == -1)
			lastModified = EntryUtils.getLastmodified(element);

		return lastModified;
	}
	public String getTitle() { return getValue(); }
	public void setTitle(String title) {
		if(titleModified || !Objects.equals(title, getTitle())) {
			titleModified = true;
			setValue(title);
			setModified();
		}
	}
	void setModified() {
		lastModified = System.currentTimeMillis();
		maneger.setModified();
	}
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content_0();
	}
	private String content_0() {
		if(content0 == null)
			content0 = EntryUtils.getContent(element);

		return content0;
	}
	public void setContent(String content) {
		if(contentModified || !Objects.equals(content, content_0())) {
			contentModified = true;
			this.content0 = content; 
			setModified();
		}
	}
	Builder<Entry> walk(Builder<Entry> collector) {
		collector.accept(this);

		for (int i = 0; i < getChildren().size(); i++)
			((Entry)getChildren().get(i)).walk(collector);

		return collector; 
	}
	public Entry addChild(String title, Entry relativeTo) {
		Entry child = new Entry(title, null, System.currentTimeMillis(), maneger);

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
	private List<TreeItem<String>> modifiedChildren() {
		childrenModified = true;
		maneger.setModified();
		return getChildren();
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
		List<TreeItem<String>> items = modifiedChildren();
		if(index < 0)
			items.addAll(0, list);
		else if(index >= items.size())
			items.addAll(list);
		else
			items.addAll(index, list);
	}
	public void addAll(List<TreeItem<String>> list) {
		modifiedChildren().addAll(list);
	}
	// special method used by DataManeger
	void setAll(Entry[] entries) {
		childrenModified = false;
		childrenSet = true;
		super.getChildren().setAll(entries);
	}
	public boolean isEmpty() {
		return getChildren().isEmpty();
	}
}