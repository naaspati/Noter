package sam.apps.jbook_reader.datamaneger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import javafx.scene.control.TreeItem;
import sam.apps.jbook_reader.datamaneger.EntryUtils.TwoValue;

/**
 * DataManeger and View Controller
 * 
 * @author Sameer
 *
 */
public class DataManeger {
	private Path jbookPath;
	private final Entry rootItem = new Entry(null, null, -1, this);
	private Document document;
	private boolean modified; 

	protected DataManeger() throws ParserConfigurationException {
		document = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder().newDocument();
	}
	protected DataManeger(Path jbookPath) throws Exception {
		Objects.requireNonNull(jbookPath, "Path to .jbook cannot be null");
		if(Files.notExists(jbookPath))
			throw new FileNotFoundException("File not found: "+jbookPath);
		if(Files.isDirectory(jbookPath))
			throw new IOException("Not a File:"+jbookPath);

		this.jbookPath = jbookPath;
		reload();
	}
	public void reload() throws Exception {
		if(jbookPath == null)
			return;

		TwoValue value = EntryUtils.parse(jbookPath, this);
		rootItem.getChildren().setAll(value.entries);
		document = value.doc;
		setModified(false);
	}
	public Entry getRootItem() {
		return rootItem;
	}
	public boolean isModified() {
		return modified;
	}
	void setModified() {
		if(!modified)
			setModified(true);
	}
	protected void setModified(boolean b) {
		this.modified = b;
	}
	public void save() throws Exception {
		save(jbookPath);
	}
	public void save(Path path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		EntryUtils.save(document, rootItem.getChildren().stream().map(i -> (Entry)i), path);
		jbookPath = path;
		setModified(false);
	}
	public Stream<Entry> walk() {
		return _walk(null);
	} 
	public Stream<Entry> _walk(TreeItem<String> item) {
		Stream.Builder<Entry> builder = Stream.builder();
		if(item == null)
			return rootItem.walk(builder).build().skip(1);
		else
			return ((Entry)item).walk(builder).build();
	}
	public Path getJbookPath() {
		return jbookPath;
	}
	public void setJbookPath(Path path) {
		jbookPath = path;
	}
}
