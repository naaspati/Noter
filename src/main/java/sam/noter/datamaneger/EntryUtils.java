package sam.noter.datamaneger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
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
import org.xml.sax.helpers.DefaultHandler;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.config.Session;
import sam.noter.Utils;


class EntryUtils {
	private static final String TITLE = "title";
	private static final String ENTRIES = "entries";
	private static final String CONTENT = "content";
	private static final String CHILDREN = "children";
	private static final String ENTRY = "entry";
	private static final String LAST_MODIFIED = "lastmodified";

	static class TwoValue {
		final EntryXML[] entries;
		final Document doc;
		TwoValue(EntryXML[] entries, Document doc) {
			this.entries = entries;
			this.doc = doc;
		}
	}
	static void parse(File path, RootEntry maneger) throws Exception {
		Document doc = 
				DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(path);

		doc.normalize();
		maneger.setDocument(doc);

		Optional.of(doc.getElementsByTagName(ENTRIES))
		.filter(list -> list.getLength() != 0)
		.map(e -> e.item(0).getChildNodes())
		.ifPresent(list -> collectChildren(list, maneger.getChildren()));
	}
	static void save(Document doc, Iterable<?> iterable, File target) throws Exception {
		clearNode(doc);

		Element rootElement = doc.createElement(ENTRIES);
		doc.appendChild(rootElement);
		
		for (Object o : iterable) 
			rootElement.appendChild(((EntryXML) o).getElement(doc));
		
		write(doc, target);
	}

	private static final String INDENT; 
	static {
		Path p = Utils.APP_DATA.resolve("xml.properties");
		if(Files.exists(p)) {

			INDENT = Optional.ofNullable(Session.getProperty(EntryUtils.class, "xml.indent"))
					.map(String::trim)
					.map(s -> s.equalsIgnoreCase("true") ? "yes" : s)
					.map(s -> s.equalsIgnoreCase("false") ? "no" : s)
					.orElse("no");
		} else {
			INDENT = null;
		}
	}

	private static void write(Document doc, File target) throws TransformerFactoryConfigurationError, TransformerException, IOException {	
		Transformer transformer = 
				TransformerFactory.newInstance()
				.newTransformer();

		if(INDENT != null)
			transformer.setOutputProperty(OutputKeys.INDENT, INDENT);

		StreamResult result = new StreamResult(target);
		transformer.transform(new DOMSource(doc), result);
	}

	public static void collectChildren(Element node, List<TreeItem<String>> sink) {
		Node n = getChild(node, CHILDREN);
		collectChildren(n == null ? null : n.getChildNodes(), sink);
	}
	private static void collectChildren(NodeList list, List<TreeItem<String>> sink) {
		int size = list == null ? 0 : list.getLength();
		if(size == 0) return;
		
		for (int i = 0; i < size; i++) {
			Node item = list.item(i);
			if(!ENTRY.equals(item.getNodeName()))
				continue;
			sink.add(new EntryXML((Element)item));
		}
	}
	public static Node getChild(Element parent, String childName) {
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
	public static Node getTitleNode(Element item) {
		return getChild(item, TITLE);
	}
	public static Node getContentNode(Element item) {
		return getChild(item, CONTENT);
	}
	public static Node getLastmodifiedNode(Element item) {
		return getChild(item, LAST_MODIFIED);
	}	
	public static String getTitle(Element item) {
		Node n = getTitleNode(item);
		return n == null ? null : n.getTextContent();
	}
	public static String getContent(Element item) {
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
	public static long getLastmodified(Element item) {
		Node n = getLastmodifiedNode(item);

		if(n == null)
			return 0;
		return Long.parseLong(n.getTextContent());
	}
	public static Element createEntry(Document doc, EntryXML entry) {
		Element element = doc.createElement(ENTRY);

		append(TITLE, entry.getTitle(), element, doc);
		append(LAST_MODIFIED, String.valueOf(entry.getLastModified()), element, doc);
		append(CONTENT, entry.getContent(), element, doc);

		return element;
	}
	private static void append(String tag, String value, Element element, Document doc) {
		if(value == null)
			return;

		Element el = doc.createElement(tag);
		if(tag == CONTENT)
			el.appendChild(doc.createCDATASection(value));
		else
			el.setTextContent(value);

		element.appendChild(el);
	}
	public static void setTitle(Element element, String title, Document doc) {
		updateNode(TITLE, title, element, doc);
	}
	public static void setContent(Element element, String content, Document doc) {
		updateNode(CONTENT, content, element, doc);
	}
	public static void setLastModified(Element element, long lastModified, Document doc) {
		updateNode(LAST_MODIFIED, String.valueOf(lastModified), element, doc);
	}
	private static void updateNode(String tag, String value, Element element, Document doc) {
		Node n = getChild(element, tag);
		if(n == null && value == null)
			return;
		if(value == null) {
			element.removeChild(n);
			return;
		}
		if(n == null)
			append(tag, value, element, doc);
		else {
			if(tag == CONTENT) {
				clearNode(n);
				n.appendChild(doc.createCDATASection(value));
			}
			else
				n.setTextContent(value);
		}

	}
	public static void clearNode(Node n) {
		while(n.hasChildNodes()) n.removeChild(n.getFirstChild());
	}
	public static void setChildren(Element element, Document doc, Stream<Element> children) {
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
		}
		else
			clearNode(n);

		children.forEach(n::appendChild);
	}
}
