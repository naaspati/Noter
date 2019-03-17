package sam.noter.dao.zip;

import java.nio.file.Path;
import java.util.Objects;

import sam.myutils.ThrowException;

class Meta {
	public final int id;
	private long lastModified;
	private Path path;
	
	public Meta(int id, long lasModified) {
		this.id = id;
		this.lastModified = lasModified;
	}
	public Meta(int id, long lasModified, Path p) {
		this(id, lasModified);
		this.path = Objects.requireNonNull(p);
	}
	public long lastModified() {
		return lastModified;
	}
	public void setPath(Path path) {
		if(this.path != null)
			ThrowException.illegalAccessError();
		
		this.path = path;
		
	}
	public Path path() {
		return path;
	}
}