package sam.noter.dao.dom;

import java.nio.file.Path;

import sam.noter.dao.RootEntry;
import sam.noter.dao.RootEntryFactory;

/**
 * DataManeger and View Controller
 * 
 * @author Sameer
 *
 */

public class RootDOMEntryFactory implements RootEntryFactory {
	@Override
	public RootEntry create(Path path) throws Exception {
		return new RootDOMEntry(path);
	}
	@Override
	public RootEntry load(Path file) throws Exception {
		return new RootDOMEntry(file);
	}
}