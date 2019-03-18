package sam.noter.dao.zip;

import java.nio.file.Path;
import java.util.Objects;

class Meta {
	public final int id;
	private long lastModified;
	private Path source;
	
	public Meta(Meta meta) {
		this(meta.id, meta.lastModified);
		this.source = Objects.requireNonNull(meta.source);
	}
	
	public Meta(int id, long lasModified) {
		this.id = id;
		this.lastModified = lasModified;
	}
	public Meta(int id, long lasModified, Path jbook_path) {
		this(id, lasModified);
		this.source = Objects.requireNonNull(jbook_path);
	}
	public long lastModified() {
		return lastModified;
	}
	public Path source() {
		return source;
	}
	public void setSource(Path jbook_path) {
		this.source = jbook_path;
	}
	protected void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}
}