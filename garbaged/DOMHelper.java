package sam.noter.dao.dom;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import sam.config.Session;
import sam.noter.Utils;


class DOMHelper {
	private static final String TITLE = "title";
	private static final String ENTRIES = "entries";
	private static final String CONTENT = "content";
	private static final String CHILDREN = "children";
	private static final String ENTRY = "entry";
	private static final String LAST_MODIFIED = "lastmodified";
	private final Document doc;

	DOMHelper() throws ParserConfigurationException {
		this.doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.newDocument();
	}
	DOMHelper(File path, List<?> items) throws Exception {
		this.doc = 
				DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(path);

		doc.normalize();

		Optional.of(doc.getElementsByTagName(ENTRIES))
		.filter(list -> list.getLength() != 0)
		.map(e -> e.item(0).getChildNodes())
		.ifPresent(list -> collectChildren(list, items));
	}
	void save(@SuppressWarnings("rawtypes") List list, File target) throws Exception {
		clearNode(doc);

		Element rootElement = doc.createElement(ENTRIES);
		doc.appendChild(rootElement);

		for (Object o : list) 
			rootElement.appendChild(((DOMEntry) o).getElement(this));

		Utils.createBackup(target);
		write(doc, target);
	}

	private String indent() {
		Path p = Utils.APP_DATA.resolve("xml.properties");
		if(Files.exists(p)) {

			return Optional.ofNullable(Session.getProperty(DOMHelper.class, "xml.indent"))
					.map(String::trim)
					.map(s -> s.equalsIgnoreCase("true") ? "yes" : s)
					.map(s -> s.equalsIgnoreCase("false") ? "no" : s)
					.orElse("no");
		} else {
			return null;
		}		
	}; 


	private void write(Document doc, File target) throws TransformerFactoryConfigurationError, TransformerException, IOException {	
		Transformer transformer = 
				TransformerFactory.newInstance()
				.newTransformer();

		String indent = indent();
		if(indent != null)
			transformer.setOutputProperty(OutputKeys.INDENT, indent);

		StreamResult result = new StreamResult(target);
		transformer.transform(new DOMSource(doc), result);
	}

	public void collectChildren(Element node, List<?> sink) {
		Node n = getChild(node, CHILDREN);
		collectChildren(n == null ? null : n.getChildNodes(), sink);
	}
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void collectChildren(NodeList list, List sink) {
		int size = list == null ? 0 : list.getLength();
		if(size == 0) return;

		for (int i = 0; i < size; i++) {
			Node item = list.item(i);
			if(!ENTRY.equals(item.getNodeName()))
				continue;
			sink.add(new DOMEntry((Element)item, this));
		}
	}
	public Node getChild(Element parent, String childName) {
		if(parent == null)
			return null; 
		NodeList list = parent.getChildNodes();

		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);
			String tag2 = n.getNodeName(); 

			if(tag2.equals(childName))
				return n;
		}
		return null;
	}
	public Node getTitleNode(Element item) {
		return getChild(item, TITLE);
	}
	public Node getContentNode(Element item) {
		return getChild(item, CONTENT);
	}
	public Node getLastmodifiedNode(Element item) {
		return getChild(item, LAST_MODIFIED);
	}	
	public String getTitle(Element item) {
		Node n = getTitleNode(item);
		return n == null ? null : n.getTextContent();
	}
	public String getContent(Element item) {
		Node n = getContentNode(item);
		if(n == null)
			return null;

		NodeList list = n.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			if(list.item(i) instanceof CharacterData) {
				CharacterData cd = (CharacterData)list.item(i); 
				return cd == null ? null : cd.getData();
			}
		}
		return null;
	}
	public long getLastmodified(Element item) {
		Node n = getLastmodifiedNode(item);

		if(n == null)
			return 0;
		return Long.parseLong(n.getTextContent());
	}
	public Element createEntryXML(DOMEntry entry) {
		Element element = doc.createElement(ENTRY);

		append(TITLE, entry.getTitle(), element);
		append(LAST_MODIFIED, String.valueOf(entry.getLastModified()), element);
		append(CONTENT, entry.getContent(), element);
		setChildren(element, entry.getChildren());

		return element;
	}
	private void append(String tag, String value, Element element) {
		if(value == null)
			return;

		Element el = doc.createElement(tag);
		if(tag == CONTENT)
			el.appendChild(doc.createCDATASection(value));
		else
			el.setTextContent(value);

		element.appendChild(el);
	}
	public void setTitle(Element element, String title) {
		updateNode(TITLE, title, element);
	}
	public void setContent(Element element, String content) {
		updateNode(CONTENT, content, element);
	}
	public void setLastModified(Element element, long lastModified) {
		updateNode(LAST_MODIFIED, String.valueOf(lastModified), element);
	}
	private void updateNode(String tag, String value, Element element) {
		Node n = getChild(element, tag);
		
		if(n == null && value == null)
			return;
		if(value == null) {
			element.removeChild(n);
			return;
		}
		if(n == null)
			append(tag, value, element);
		else {
			if(tag == CONTENT) {
				clearNode(n);
				n.appendChild(doc.createCDATASection(value));
			}
			else
				n.setTextContent(value);
		}

	}
	
	public void clearNode(Node n) {
		while(n.hasChildNodes()) n.removeChild(n.getFirstChild());
	}
	public void setChildren(Element element, @SuppressWarnings("rawtypes") List children) {
		Node n = getChild(element, CHILDREN);
		if(n == null && children == null)
			return;
		if(children == null) {
			element.removeChild(n);
			return;
		}
		if(n == null) {
			n = doc.createElement(CHILDREN);
			element.appendChild(n);
		} else
			clearNode(n);

		for (Object s : children) 
			n.appendChild(((DOMEntry)s).getElement(this));			
	}
}
