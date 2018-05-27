package sam.apps.jbook_reader.datamaneger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream.Builder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.scene.control.TreeItem;

public class Entry extends TreeItem<String> { 
	private String content;
	private long lastModified = -1;
	private boolean titleModified, contentModified, childrenModified;
	private Element element;
	private final DataManeger maneger;

	public static Entry cast(TreeItem<String> item) {
		return (Entry)item;
	}
	Entry(String title, String content, long lastModified, DataManeger maneger) {
		super(title);
		this.content = content;
		this.lastModified = lastModified;
		this.maneger = maneger;
	}
	Entry(Element element, DataManeger maneger) {
		this.element = element;
		setValue(EntryUtils.getTitle(element));
		getChildren().setAll(EntryUtils.getChildren(element, maneger));
		this.maneger = maneger;
	}
	Element getElement(Document doc) {
		log(); //TODO remove
		
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
			EntryUtils.setContent(element, content, doc);
			contentModified = false;
		}
		if(childrenModified) {
			EntryUtils.setChildren(element, doc, getChildren().isEmpty() ? null : getChildren().stream().map(t -> ((Entry)t).getElement(doc)));
			childrenModified = false;
		}
		else {
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
		if(content == null)
			content = EntryUtils.getContent(element);

		return content; 
	}
	public void setContent(String content) {
		if(contentModified || !Objects.equals(content, this.content)) {
			contentModified = true;
			this.content = content; 
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
		modifiedChildren().addAll(index, list);
	}
	public void addAll(List<TreeItem<String>> list) {
		modifiedChildren().addAll(list);
	}	
}