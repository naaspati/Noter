package sam.noter.dao;

import java.nio.file.Path;

import sam.di.Injector;
import sam.noter.dao.api.IRootEntry;
import sam.noter.dao.zip.RootEntryZFactory;

public interface RootEntryFactory {
	
	public static RootEntryFactory getInstance() throws Exception{
		return RootEntryZFactory.getInstance();
		// return new RootDOMEntryFactory();
	}
	
	IRootEntry create(Injector injector, Path file) throws Exception;
	IRootEntry load(Injector injector, Path file) throws Exception;
}
