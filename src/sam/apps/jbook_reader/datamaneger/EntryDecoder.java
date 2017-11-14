package sam.apps.jbook_reader.datamaneger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class EntryDecoder {

	List<Entry> decode(Path path) throws Exception {
		Document doc = 
				DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(path.toFile());

		doc.normalize();

		NodeList entriesList = doc.getElementsByTagName("entries");

		if(entriesList.getLength() == 0)
			return new ArrayList<>();

		return parseEntries(entriesList.item(0).getChildNodes());
	}

	private List<Entry> parseEntries(NodeList list) {
		return IntStream.range(0, list.getLength())
				.mapToObj(list::item)
				.filter(item -> "entry".equals(item.getNodeName()))
				.map(this::newEntry)
				.collect(Collectors.toList());
	}
	private Entry newEntry(Node item) {
		String title = null, content = null;
		long lastmodified = 0;
		List<Entry> children = null;

		NodeList list = item.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			Node n = list.item(i);

			switch (n.getNodeName()) {
			case "title":
				title = n.getTextContent();
				break;
			case "content":
				CharacterData cd = (CharacterData)n.getFirstChild(); 
				content = cd.getData();
				break;
			case "lastmodified":
				lastmodified = Long.parseLong(n.getTextContent());
				break;
			case "children":
				children = parseEntries(n.getChildNodes());
				break;
			case "#text":
				break;
			default:
				Logger.getGlobal().warning("unknown tag: "+n.getNodeName());
				break;
			}
		}
		Entry e = new Entry(title, content, lastmodified);
		if(children != null)
			e.getChildren().setAll(children);
		return e;
	}

}
