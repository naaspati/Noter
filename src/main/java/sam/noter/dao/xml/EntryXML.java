package sam.noter.dao.xml;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream.Builder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.scene.control.TreeItem;
import sam.noter.dao.Entry;

class EntryXML extends Entry {
	protected boolean titleModified, contentModified, childrenModified;
	private Element element;
	
	EntryXML() {}
	
	EntryXML(String title, String content, long lastModified) {
		super(title, content, lastModified);
		this.content = content;
		this.lastModified = lastModified;
	}
	EntryXML(Element element) {
		this.element = element;
		setValue(EntryXMLUtils.getTitle(element));
	}
	
	@Override
	protected void loadChildren(List<TreeItem<String>> sink)  {
		if(element != null)
			EntryXMLUtils.collectChildren(element, sink);
	}
	Element getElement(Document doc) {
		if(!(element == null || titleModified || contentModified || childrenModified))
			return element;

		log();

		if(element == null) {
			element = EntryXMLUtils.createEntryXML(doc, this);
			contentModified = false;
			titleModified = false;
		}
		if(titleModified) {
			EntryXMLUtils.setTitle(element, getTitle(), doc);
			titleModified = false;
		}
		if(contentModified) {
			EntryXMLUtils.setContent(element, getContent(), doc);
			contentModified = false;
		}
		if(childrenModified) {
			EntryXMLUtils.setChildren(element, doc, getChildren().isEmpty() ? null : getChildren().stream().map(t -> ((EntryXML)t).getElement(doc)));
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
	
	public void setTitle(String title) {
		if(titleModified || !Objects.equals(title, getTitle())) {
			titleModified = true;
			setValue(title);
			setModified();
		}
	}
	protected void setModified() {
		if(((EntryXML)getParent()) != null)
			((EntryXML)getParent()).setChildModified();
	}
	protected void setChildModified() {
		childrenModified = true;
		setModified();
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
	@Override
	public long getLastModified() {
		if(lastModified == -1)
			lastModified = EntryXMLUtils.getLastmodified(element);
		return lastModified;
	}
	@Override
	public String getContent() {
		if(contentProxy != null) return contentProxy.get();
		return content_0();
	}
	private String content_0() {
		if(content == null)
			content = EntryXMLUtils.getContent(element);

		return content;
	}
	@Override
	public void setContent(String content) {
		if(contentModified || !Objects.equals(content, content_0())) {
			contentModified = true;
			this.content = content; 
			setModified();
			lastModified = System.currentTimeMillis();
		}
	}

	private List<TreeItem<String>> modifiedChildren() {
		childrenModified = true;
		setModified();
		return getChildren();
	}
	@Override
	public void addAll(List<TreeItem<String>> list) {
		modifiedChildren().addAll(list);
	}
	@Override
	public boolean remove(TreeItem<String> t) {
		return modifiedChildren().remove(t);
	}
	@Override
	public boolean add(Entry child) {
		return modifiedChildren().add(child);
	}
	@Override
	public void add(int index, Entry child) {
		modifiedChildren().add(index, child);
	}
	@Override
	public void addAll(int index, List<TreeItem<String>> list) {
		addAll(index, list, modifiedChildren());
	}
	@Override
	protected Entry newEntry(String title, String content, long currentTimeMillis) {
		return new EntryXML(title, content, currentTimeMillis);
	}
	@Override
	public void resetRootEntry() {
		removeElement();
	}
	@Override
	protected Builder<Entry> walk(Builder<Entry> collector) {
		return super.walk(collector);
	}
}
