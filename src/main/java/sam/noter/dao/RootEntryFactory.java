package sam.noter.dao;

import java.nio.file.Path;
import java.util.List;

import sam.noter.dao.api.IRootEntry;

public interface RootEntryFactory {
	List<Path> recentsFiles(); 
	IRootEntry create(Path file) throws Exception;
	IRootEntry load(Path file) throws Exception;
}
