package sam.noter.dao.dom;

import static sam.myutils.Checker.isEmpty;
import static sam.noter.Utils.TEMP_DIR;
import static sam.noter.Utils.addOnStop;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import org.xml.sax.SAXException;

import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.LongSerializer;
import sam.logging.MyLoggerFactory;
import sam.noter.dao.Entry;
import sam.reference.WeakAndLazy;
@SuppressWarnings("rawtypes")
class DOMLoader {
	private static final Logger LOGGER = MyLoggerFactory.logger(DOMLoader.class);
	private static final Path BACKUP_DIR = TEMP_DIR.resolve(DOMLoader.class.getName()+"/"+LocalDate.now());
	
	static {
		BACKUP_DIR.toFile().mkdirs();
		Path path = BACKUP_DIR.resolveSibling("backup.schedule");
		try {
			if(Files.exists(path) && LongSerializer.read(path) >= System.currentTimeMillis()) {
				LongSerializer.write(System.currentTimeMillis()+Duration.ofDays(7).toMillis(), path);
				addOnStop(() -> backupClean());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private static final String ID = "id";
	private static final String TITLE = "title";
	private static final String MAX_ID = "maxid";
	private static final String ROOT = "root";
	private static final String ENTRIES = "entries";
	private static final String CONTENT = "content";
	private static final String CHILDREN = "children";
	private static final String ENTRY = "entry";
	private static final String LAST_MODIFIED = "lastmodified";

	private final Document doc;

	private int maxId;
	private Node entriesNode, maxIdNode, docRootNode;

	private final RootDOMEntry root;
	

	DOMLoader(RootDOMEntry root) throws ParserConfigurationException {
		this.root = root;
		this.doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.newDocument();
	}
	DOMLoader(File path, List<?> items, RootDOMEntry root) throws SAXException, IOException, ParserConfigurationException {
		this.root = root;
		this.doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(path);

		doc.normalize();
		// converting old format to new format
		each(doc.getChildNodes(), node -> {
			switch (node.getNodeName()) {
				case ROOT:
					docRootNode = node;
					break;
				case ENTRIES:
					entriesNode = node;
					break;
			}
		});

		if(entriesNode != null) {
			doc.removeChild(entriesNode);
			docRootNode = doc.createElement(ROOT);
			doc.appendChild(docRootNode);
			docRootNode.appendChild(entriesNode);
		}

		each(docRootNode.getChildNodes(), node -> {
			switch (node.getNodeName()) {
				case MAX_ID:
					maxIdNode = node;
					break;
				case ENTRIES:
					entriesNode = node;
					break;
			}
		});

		if(entriesNode != null)
			collectChildren(entriesNode.getChildNodes(), items);

		if(maxIdNode != null) 
			maxId = Integer.parseInt(maxIdNode.getTextContent());

		/**
		 * Optional.ofNullable(doc.getElementsByTagName(ENTRIES))
		.filter(list -> list.getLength() != 0)
		.map(e -> e.item(0).getChildNodes())
		.ifPresent(list -> collectChildren(root, list, items));
		 */
	}
	private void eachChild(Node node, Consumer<Node> action){
		if(node == null) return;
		each(node.getChildNodes(), action);
	}
	private void each(NodeList list, Consumer<Node> action) {
		int size = list == null ? 0 : list.getLength();
		if(size == 0) return;

		for (int i = 0; i < size; i++) {
			action.accept(list.item(i));
		}
	}
	void save(List list, File target) throws Exception {
		if(docRootNode == null){
			docRootNode = doc.createElement(ROOT);
			doc.appendChild(docRootNode);
		}

		if(maxIdNode == null){
			maxIdNode = doc.createElement(MAX_ID);
			docRootNode.appendChild(maxIdNode);
		}
		if(entriesNode == null){
			entriesNode = doc.createElement(ENTRIES);
			docRootNode.appendChild(entriesNode);
		}

		maxIdNode.setTextContent(String.valueOf(maxId));
		updateChildren("ROOT", docRootNode, entriesNode, list);

		createBackup(target);
		write(doc, target);
	}
	

	public static void createBackup(File file) {
		if(file == null || !file.exists())
			return;
		
		try {
			Files.copy(file.toPath(), BACKUP_DIR.resolve(file.getName()+"_SAVED_ON_"+LocalDateTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)).replace(':', '_')), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "failed to backup: "+file, e);
		}
	}


	private static void backupClean() {
		File backup = BACKUP_DIR.getParent().toFile();
		if(!backup.exists()) return;
		
		LocalDateTime now = LocalDateTime.now();
		
		for (String s : backup.list()) {
			LocalDate date = LocalDate.parse(s);
			if(Duration.between(date.atStartOfDay(), now).toDays() > 3){
				FilesUtilsIO.delete(new File(backup, s));
				LOGGER.info("DELETE backup(s): "+s);
			}
		}
	}
	

	private void write(Document doc, File target) throws TransformerFactoryConfigurationError, TransformerException, IOException {	
		Transformer transformer = 
				TransformerFactory.newInstance()
				.newTransformer();

		transformer.setOutputProperty(OutputKeys.INDENT, "yes");
		transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");

		StreamResult result = new StreamResult(target);
		transformer.transform(new DOMSource(doc), result);
		LOGGER.info("XML SAVED: "+target);
	}


	public void collectChildren(Node childrenNode, List sink) {
		if(childrenNode == null) return;
		collectChildren(childrenNode.getChildNodes(), sink);
	}

	@SuppressWarnings({ "unchecked"})
	private void collectChildren(NodeList list, List sink) {
		each(list, node -> {
			if(ENTRY.equals(node.getNodeName()))
				sink.add(newDOMEntry(node));
		});
	}

	class DomEntryInit {
		private int idN = -10; 
		private Node rootNode, id, title, content, children, lastModified;

		private  DomEntryInit() {}

		public int id() {
			if(idN != -10) return idN;
			if(id == null)
				return idN = maxId++;

			return idN = Integer.parseInt(id.getTextContent());
		}
		public String title() {
			return title == null ? null : title.getTextContent();
		}
		void collectChildren(List items) {
			DOMLoader.this.collectChildren(children, items);
		}

		public String content() {
			String string[] = {null};
			eachChild(content, n -> {
				if(n instanceof CharacterData) {
					CharacterData cd = (CharacterData)n; 
					string[0] = cd == null ? null : cd.getData();
				}				
			});

			return string[0];
		}
		public long lastModified() {
			if(lastModified == null) return 0;
			return Long.parseLong(lastModified.getTextContent());
		}
		public boolean isNew() {
			return rootNode == null;
		}
		public RootDOMEntry getRoot() {
			return root;
		}
		public void createRootNode(DOMEntry entry) {
			if(entry == null)
				return;
			if(entry.dom() != this)
				throw new IllegalStateException();

			rootNode = doc.createElement(ENTRY);

			id = append(ID, String.valueOf(entry.getId()), rootNode);
			title = append(TITLE, entry.getTitle(), rootNode);
			lastModified =  append(LAST_MODIFIED, String.valueOf(entry.getLastModified()), rootNode);
			content = append(CONTENT, entry.getContent(), rootNode);
			children = setChildren(entry.getChildren(), this);
		}
	}

	private DOMEntry newDOMEntry(Node source) {
		DomEntryInit t = new DomEntryInit();
		t.rootNode = source;

		each(source.getChildNodes(), n -> {
			switch (n.getNodeName()) {
				case ID:
					t.id = n;
					break;
				case TITLE:
					t.title = n;
					break;
				case CONTENT:
					t.content = n;
					break;
				case CHILDREN:
					t.children = n;
					break;
				case LAST_MODIFIED: 
					t.lastModified = n;
					break;
				default:
					break;
			}
		});

		if(t.id == null) {
			t.id = append(ID, String.valueOf(t.id()), source);
			LOGGER.fine(() -> "SET ID TO OLD DATA: "+t.title()+" ("+t.id()+")");
		}

		return new DOMEntry(t); 
	}

	private Node append(String tag, String value, Node parent) {
		if(value == null)
			return null;

		Element el = doc.createElement(tag);
		if(tag == CONTENT)
			el.appendChild(doc.createCDATASection(value));
		else
			el.setTextContent(value);

		parent.appendChild(el);
		return el;
	}
	private void updateNode(String tag, String value, DomEntryInit dom, Node node) {
		if(node == null && value == null)
			return;

		if(value == null) {
			dom.rootNode.removeChild(node);
			return;
		}
		if(node == null)
			append(tag, value, dom.rootNode);
		else {
			if(tag == CONTENT) {
				while(node.hasChildNodes()) node.removeChild(node.getFirstChild());

				node.appendChild(doc.createCDATASection(value));
			}
			else
				node.setTextContent(value);
		}
	}


	private Node update(Object s) {
		DOMEntry d = (DOMEntry)s;
		if(!d.isModified()) return d.dom().rootNode;
		logModification(d);

		DomEntryInit dom = d.dom();

		if(dom.rootNode == null) {
			dom.createRootNode(d);
			d.clearModified();
			LOGGER.fine(() -> "NEW: "+d);
			return dom.rootNode;
		}
		if(d.isModified()){
			updateNode(LAST_MODIFIED, String.valueOf(d.getLastModified()), dom, dom.lastModified);
			LOGGER.fine(() -> "UPDATE LAST_MODIFIED: "+d);
		}
		if(d.isTitleModified()) {
			updateNode(TITLE, d.getTitle(), dom, dom.title);
			LOGGER.fine(() -> "UPDATE TITLE: "+d);
		} if(d.isContentModified()){ 
			updateNode(CONTENT, d.getContent(), dom, dom.content);
			LOGGER.fine(() -> "UPDATE CONTENT: "+d);
		} if(d.isChildrenModified()) {
			updateChildren(d, d.dom().rootNode, d.dom().children, d.getChildren());
			LOGGER.fine(() -> "UPDATE CHILDREN: "+d);
		}
		d.clearModified();

		return dom.rootNode;
	}
	private void updateChildren(Object owner, Node ownerNode, Node currentChildrenNode, List children) {
		if(currentChildrenNode == null)
			setChildren(children, currentChildrenNode, ownerNode);
		else {
			NodeList nodes = currentChildrenNode.getChildNodes();
			int nodesSize = nodes.getLength();
			int nodeN = -1;
			int childN = -1;

			if(nodesSize == 0){
				ownerNode.removeChild(currentChildrenNode);
			} else {
				for (nodeN = 0; nodeN < nodesSize; nodeN++) {
					Node node = nodes.item(nodeN);
					if(!node.getNodeName().equals(ENTRY))
						continue;
					DOMEntry e = (DOMEntry) children.get(++childN); 
					if(!node.equals(e.dom().rootNode))
						break;
					update(e);					
				}
				
				if(nodeN < nodesSize){
					Node[] remove = new Node[nodesSize - nodeN];
					int k = nodeN;
					int cn = childN; 
					LOGGER.fine(() -> "RELOCATE/REMOVE CHILDREN: "+owner+" STARTING AT:"+" -> ["+k+"("+children.get(cn)+"),"+nodesSize+")");
					
					int n = 0;
					for (int j = nodeN; j < nodesSize; j++)
						remove[n++] = nodes.item(j);

					for (Node node : remove)
						currentChildrenNode.removeChild(node);
				}
				if(childN < children.size()){
					int k = childN;
					LOGGER.fine(() -> "ADDED CHILDREN: "+owner+" -> "+(children.size() - k));
					for (int j = childN; j < children.size(); j++)
						currentChildrenNode.appendChild(update(children.get(j)));
				}
			}
		}
	}
	public Node setChildren(List children, DomEntryInit d) {
		return setChildren(children, d.children, d.rootNode);
	}
	private Node setChildren(List newChildren, Node currentChildrenNode, Node ownerNode) {
		if(currentChildrenNode != null)
			ownerNode.removeChild(currentChildrenNode);

		currentChildrenNode = doc.createElement(CHILDREN);
		ownerNode.appendChild(currentChildrenNode);

		if(isEmpty(newChildren))
			return currentChildrenNode;

		for (Object o : newChildren) 
			currentChildrenNode.appendChild(update(o));

		return currentChildrenNode;
	}
	
	private static WeakAndLazy<StringBuilder> logSB = new WeakAndLazy<>(StringBuilder::new);
	
	private void logModification(Entry e) {
		LOGGER.info(() -> {
			synchronized(logSB) {	
				StringBuilder sb = logSB.get();
				sb.setLength(0);

				sb.append(e).append(" [");
				if(e.isTitleModified())
					sb.append("TITLE, ");
				if(e.isContentModified())
					sb.append("CONTENT, ");
				if(e.isChildrenModified())
					sb.append("CHILDREN, ");
				sb.append(']');
				
				return sb.toString();
			}
		});
		
	}
	public DOMEntry newEntry(String title) {
		return new DOMEntry(new DomEntryInit(), title);
	}
	public DOMEntry newEntry(DOMEntry d) {
		return new DOMEntry(new DomEntryInit(), d.getTitle(), d.getContent(), d.getLastModified());
	}
}
