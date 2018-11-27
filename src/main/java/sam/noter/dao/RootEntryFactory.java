package sam.noter.dao;

import java.nio.file.Path;

import sam.noter.dao.zip.RootEntryZFactory;

public interface RootEntryFactory {
	
	public static RootEntryFactory getInstance() throws Exception{
		return RootEntryZFactory.getInstance();
		// return new RootDOMEntryFactory();
	}
	
	RootEntry create(Path file) throws Exception;
	RootEntry load(Path file) throws Exception;
}
