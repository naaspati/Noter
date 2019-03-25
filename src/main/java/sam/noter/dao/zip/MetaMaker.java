package sam.noter.dao.zip;

import java.nio.file.Path;

@FunctionalInterface
public interface MetaMaker {
	public Meta newInstance(int id, long lastModified, Path path);

}
