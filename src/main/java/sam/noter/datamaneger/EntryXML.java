package sam.noter.datamaneger;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream.Builder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public class EntryXML extends TreeItem<String> {

	private String content0;
	private long lastModified = -1;
	protected boolean titleModified, contentModified, childrenModified, childrenSet;
	private Element element;
	private Supplier<String> contentProxy;
	
	protected EntryXML() {}

	EntryXML(String title, String content, long lastModified) {
		super(title);
		this.content0 = content;
		this.lastModified = lastModified;
	}
	public void setContentProxy(Supplier<String> contentProxy) {
		this.contentProxy = contentProxy;
	}
	EntryXML(Element element) {
		this.element = element;
		setValue(EntryUtils.getTitle(element));
	}
	@Override
	public ObservableList<TreeItem<String>> getChildren() {
		loadChildren();
		return super.getChildren();
	}
	protected void loadChildren() {
		if(childrenSet) return;

		if(element != null)
			EntryUtils.collectChildren(element, super.getChildren());
		childrenSet = true;
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
			EntryUtils.setChildren(element, doc, getChildren().isEmpty() ? null : getChildren().stream().map(t -> ((EntryXML)t).getElement(doc)));
			childrenModified = false;
		} else {
			for (int i = 0; i < getChildren().size(); i++)
				((EntryXML)getChildren().get(i)).getElement(doc);
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
		setTitle(getTitle());
		setContent(getContent());
		modifiedChildren();
		
		titleModified = true; 
		contentModified  = true; 
		childrenModified = true;

		getChildren().forEach(e -> ((EntryXML)e).removeElement());
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
			lastModified = System.currentTimeMillis();
		}
	}
	Builder<EntryXML> walk(Builder<EntryXML> collector) {
		collector.accept(this);

		for (int i = 0; i < getChildren().size(); i++)
			((EntryXML)getChildren().get(i)).walk(collector);

		return collector; 
	}
	public EntryXML addChild(String title, EntryXML relativeTo) {
		EntryXML child = new EntryXML(title, null, System.currentTimeMillis());

		if(relativeTo == null)
			add(child);
		else {
			int index = indexOf(relativeTo);
			index = index < 0 ? getChildren().size() : index + 1;
			add(index, child);
		}
		return child;
	}
	public int indexOf(EntryXML item) {
		return getChildren().indexOf(item);
	}
	private List<TreeItem<String>> modifiedChildren() {
		childrenModified = true;
		setModified();
		return getChildren();
	}
	public boolean remove(TreeItem<String> t) {
		return modifiedChildren().remove(t);
	}
	public boolean add(EntryXML child) {
		return modifiedChildren().add(child);
	}
	public void add(int index, EntryXML child) {
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
	public boolean isEmpty() {
		return getChildren().isEmpty();
	}
	private void setModified() {
		if(((EntryXML)getParent()) != null)
			((EntryXML)getParent()).setChildModified();
	}
	protected void setChildModified() {
		childrenModified = true;
	}
}