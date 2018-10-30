package sam.noter.dao;

import java.io.File;

import sam.noter.dao.xml.RootEntryXMLFactory;

public interface RootEntryFactory {
	
	public static RootEntryFactory getInstance(){
		return new RootEntryXMLFactory();
	}
	
	RootEntry create() throws Exception;
	RootEntry load(File file) throws Exception;
}
