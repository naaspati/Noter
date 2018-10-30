package sam.noter.datamaneger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import javafx.scene.control.TreeItem;

/**
 * DataManeger and View Controller
 * 
 * @author Sameer
 *
 */
public class RootEntry extends EntryXML {
	private File jbookPath;
	private Document document;
	private boolean modified; 

	protected RootEntry() throws ParserConfigurationException {
		document = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.newDocument();
	}
	protected RootEntry(File jbookPath) throws Exception {
		Objects.requireNonNull(jbookPath, "Path to .jbook cannot be null");
		if(!jbookPath.exists())
			throw new FileNotFoundException("File not found: "+jbookPath);
		if(jbookPath.isDirectory())
			throw new IOException("Not a File:"+jbookPath);

		this.jbookPath = jbookPath;
		reload();
	}
	public void reload() throws Exception {
		if(jbookPath == null)
			return;

		modified = false;
		getChildren().clear();
		EntryUtils.parse(jbookPath, this);
		notifyModified();
	}

	protected void notifyModified() {
	}
	//override default behaviour
	@Override
	protected void loadChildren() {}

	public boolean isModified() {
		return modified;
	}

	public void save() throws Exception {
		save(jbookPath);
	}
	public void save(File path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		EntryUtils.save(document, getChildren(), path);
		jbookPath = path;
		modified = false;
		notifyModified();
	}
	public Stream<EntryXML> walk() {
		return _walk(null);
	} 
	public Stream<EntryXML> _walk(TreeItem<String> item) {
		Stream.Builder<EntryXML> builder = Stream.builder();
		if(item == null)
			return this.walk(builder).build().skip(1);
		else
			return ((EntryXML)item).walk(builder).build();
	}
	public File getJbookPath() {
		return jbookPath;
	}
	public void setJbookPath(File path) {
		jbookPath = path;
	}

	public void setDocument(Document doc) {
		this.document = doc;
	}
	@Override
	protected void setChildModified() {
		super.setChildModified();
		if(modified) return;

		this.modified = true;
		notifyModified();
	}
}
