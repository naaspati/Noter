package sam.noter.dao.dom;

import java.nio.file.Path;

import sam.di.Injector;
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
	public IRootEntry create(Injector injector, Path path) throws Exception {
		return Junk.notYetImplemented();//TODO will fix, when used new RootDOMEntry(injector, path);
	}
	@Override
	public IRootEntry load(Injector injector, Path file) throws Exception {
		return Junk.notYetImplemented();//TODO will fix, when used return new RootDOMEntry(injector, file);
	}
}