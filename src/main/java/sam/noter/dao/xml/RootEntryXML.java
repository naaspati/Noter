package sam.noter.dao.xml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;

import javafx.scene.control.TreeItem;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;

class RootEntryXML extends EntryXML implements RootEntry {
	private File jbookPath;
	private Document document;
	private boolean modified; 
	private Runnable onModified;

	public RootEntryXML() throws ParserConfigurationException {
		document = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.newDocument();
	}
	protected RootEntryXML(File jbookPath) throws Exception {
		Objects.requireNonNull(jbookPath, "Path to .jbook cannot be null");
		if(!jbookPath.exists())
			throw new FileNotFoundException("File not found: "+jbookPath);
		if(jbookPath.isDirectory())
			throw new IOException("Not a File:"+jbookPath);

		this.jbookPath = jbookPath;
		reload();
	}

	@Override
	public void reload() throws Exception {
		if(jbookPath == null)
			return;

		modified = false;
		getChildren().clear();
		EntryXMLUtils.parse(jbookPath, this);
		notifyModified();
	}

	@Override
	public void setOnModified(Runnable action) {
		this.onModified = action;
	}
	protected void notifyModified() {
		if(onModified != null)
			onModified.run();
	}

	//override default behaviour 
	@Override protected void loadChildren(List<TreeItem<String>> sink) {}
	public void setDocument(Document doc) { this.document = doc; }

	@Override public boolean isModified() { return modified; }

	@Override
	public void save(File path) throws Exception {
		if(!isModified() && jbookPath != null)
			return;

		EntryXMLUtils.save(document, getChildren(), path);
		jbookPath = path;
		modified = false;
		notifyModified();
	}

	@Override public File getJbookPath() { return jbookPath; }
	@Override public void setJbookPath(File path) { jbookPath = path; }

	@Override
	protected void setChildModified() {
		super.setChildModified();
		if(modified) return;

		this.modified = true;
		notifyModified();
	}

	@Override
	public Stream<Entry> walk() {
		return _walk(null);
	} 
	public Stream<Entry> _walk(TreeItem<String> item) {
		Stream.Builder<Entry> builder = Stream.builder();
		if(item == null)
			return this.walk(builder).build().skip(1);
		else
			return ((EntryXML)item).walk(builder).build();
	}
}
