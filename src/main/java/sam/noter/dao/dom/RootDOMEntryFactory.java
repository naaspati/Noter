package sam.noter.dao.dom;

import java.nio.file.Path;
import java.util.List;

import sam.nopkg.Junk;
import sam.noter.dao.RootEntryFactory;
import sam.noter.dao.api.IRootEntry;

/**
 * DataManeger and View Controller
 * 
 * @author Sameer
 *
 */

public class RootDOMEntryFactory implements RootEntryFactory {
	@Override
	public IRootEntry create(Path path) throws Exception {
		return Junk.notYetImplemented();// will fix, when used new RootDOMEntry(injector, path);
	}
	@Override
	public IRootEntry load(Path file) throws Exception {
		return Junk.notYetImplemented();// will fix, when used return new RootDOMEntry(injector, file);
	}
	@Override
	public List<Path> recentsFiles() {
		return Junk.notYetImplemented(); //TODO
	}
}