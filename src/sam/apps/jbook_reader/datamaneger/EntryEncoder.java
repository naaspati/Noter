package sam.apps.jbook_reader.datamaneger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javafx.scene.control.TreeItem;
import sam.properties.session.Session;

class EntryEncoder {
	private static transient EntryEncoder instance;

	public static EntryEncoder getInstance() throws ParserConfigurationException {
		if (instance == null) {
			synchronized (EntryEncoder.class) {
				if (instance == null)
					instance = new EntryEncoder();
			}
		}
		return instance;
	}
	void encode(List<Entry> entries, Path target) throws Exception {
		Document doc = 
				DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.newDocument();
		
		Element rootElement = doc.createElement("entries");
		doc.appendChild(rootElement);
		
		for (Entry e : entries)
			serialize(e, rootElement, doc);
		
		write(doc, target);

	}
	private void serialize(Entry entry, Element parent, Document doc) {
		Element element = doc.createElement("entry");
		parent.appendChild(element);

		append("title", entry.getTitle(), element, doc);
		append("lastmodified", String.valueOf(entry.getLastModified()), element, doc);
		append("content", entry.getContent(), element, doc);
		
		List<TreeItem<String>> entries = entry.getChildren();

		if(entries == null || entries.isEmpty())
			return;

		Element childrenElement = doc.createElement("children");
		element.appendChild(childrenElement);

		for (TreeItem<String> e : entries) 
			serialize((Entry)e, childrenElement, doc);
	}
	private void append(String tag, String value, Element element, Document doc) {
		if(value == null)
			return;

		Element el = doc.createElement(tag);
		el.appendChild(tag == "content" ? doc.createCDATASection(value) : doc.createTextNode(value));
		element.appendChild(el);
	}
	private void write(Document doc, Path target) throws TransformerFactoryConfigurationError, TransformerException, IOException {	
		Transformer transformer = 
				TransformerFactory.newInstance()
				.newTransformer();
		
		if(Session.has("xml-indent"))
			Session.put("xml-indent", "false");

		transformer.setOutputProperty(OutputKeys.INDENT, 
				Optional.ofNullable(Session.get("xml-indent"))
				.map(String::trim)
				.map(s -> s.equalsIgnoreCase("true") ? "yes" : s)
				.map(s -> s.equalsIgnoreCase("false") ? "no" : s)
				.orElse("no")
				);

		transformer.setOutputProperty(OutputKeys.INDENT, "yes");

		StreamResult result = new StreamResult(Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
		transformer.transform(new DOMSource(doc), result);
	}
}
