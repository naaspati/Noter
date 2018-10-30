package sam.noter.dao.xml;

import java.io.File;

import sam.noter.dao.RootEntry;
import sam.noter.dao.RootEntryFactory;

/**
 * DataManeger and View Controller
 * 
 * @author Sameer
 *
 */

public class RootEntryXMLFactory implements RootEntryFactory {
	@Override
	public RootEntry create() throws Exception {
		return new RootEntryXML();
	}
	@Override
	public RootEntry load(File file) throws Exception {
		return new RootEntryXML(file);
	}
}