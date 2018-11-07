package sam.noter.dao;

import java.io.File;

import sam.noter.dao.dom.RootDOMEntryFactory;

public interface RootEntryFactory {
	
	public static RootEntryFactory getInstance(){
		return new RootDOMEntryFactory();
	}
	
	RootEntry create() throws Exception;
	RootEntry load(File file) throws Exception;
}
