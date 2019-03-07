package sam.noter.dao;

import java.nio.file.Path;

import sam.noter.dao.api.IRootEntry;

public interface RootEntryFactory {
	IRootEntry create(Path file) throws Exception;
	IRootEntry load(Path file) throws Exception;
}
