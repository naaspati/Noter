package sam.noter.dao.zip;

import java.nio.file.Path;

interface Meta {
    int getId();
	Path source();
	long getLastModified();
}