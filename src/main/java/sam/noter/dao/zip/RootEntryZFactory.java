package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.di.ConfigManager;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.myutils.Checker;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Resources;
import sam.noter.dao.RootEntryFactory;

@Singleton
public class RootEntryZFactory implements RootEntryFactory, AutoCloseable {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	private final Logger logger;
	private final Path mydir;
	private final TextInFile index;
	private final TextInFile content;
	private final MetaHandler metas;
	
	class Meta {
		static final int BYTES = DataMeta.BYTES + Integer.BYTES;

		public final int id;
		private DataMeta meta;
		private boolean isNew;
		private long lastModified;
		private DataMeta[] contents;

		private Meta(int id, DataMeta meta) {
			this.id = id;
			this.meta = meta;
		}
	}

	@Inject
	public RootEntryZFactory(ConfigManager config) throws IOException {
		singleton.init();

		this.logger = LogManager.getLogger(getClass());
		this.mydir = config.tempDir().resolve(getClass().getName());
		Files.createDirectories(mydir);

		Path meta = mydir.resolve("meta");
		Path index = mydir.resolve("index");
		Path content = mydir.resolve("content");

		Path[] paths = {meta, index, content};
		if(Checker.anyMatch(Files::notExists, paths)) {
			for (Path p : paths) 
				Files.deleteIfExists(p);
		}

		boolean b = Files.exists(index);

		this.metas = new MetaHandler(meta) {
			@Override
			protected DataMeta getMeta(Meta meta) {
				return meta.meta;
			}
			@Override
			protected void setMeta(Meta meta, DataMeta dm) {
				meta.meta = dm;
			}
			@Override
			protected Meta newMeta(int id, DataMeta dm) {
				return new Meta(id, dm);
			}
		};
		
		this.content = new TextInFile(content, !b);
		this.index = new TextInFile(index, !b);
	}

	@Override
	public void close() throws Exception {
		metas.close();
	}

	@Override
	public RootEntryZ create(Path path) throws Exception {
		if(Files.exists(path))
			throw new IOException("file already exists: "+path);
		
		return create0(path.normalize().toAbsolutePath());
	}
	private RootEntryZ create0(Path path) throws Exception {
		Meta m = metas.put(path);
		m.isNew = true;
		
		return new RootEntryZ(m, path, this);
	}

	@Override
	public RootEntryZ load(Path path) throws Exception {
		if(!Files.isRegularFile(path))
			throw new IOException("file not found: "+path);
		
		path =  path.normalize().toAbsolutePath();
		Meta meta = metas.get(path);
		
		if(meta == null)
			return create0(path);
		
		if(meta.lastModified != path.toFile().lastModified()) {
			logger.debug("RESET CACHE: because: meta.lastModified({}) != path.toFile().lastModified({}),  path: {}", meta.lastModified, path.toFile().lastModified(), path);
			return create0(path);
		}
		
		return new RootEntryZ(meta, path, this);
	}

	public void close(RootEntryZ root) {
		// TODO Auto-generated method stub
	}

	public ArrayWrap<EntryZ> getEntries(RootEntryZ root) {
		// TODO Auto-generated method stub
		return null;
	}

	public void save(RootEntryZ root, Path file) {
		// TODO Auto-generated method stub
	}

	public String readContent(RootEntryZ root, EntryZ e) throws IOException {
		if(root.meta.isNew)
			return "";
		
		if(e.getId() >= root.meta.contents.length)
			return "";
		
		DataMeta dm = root.meta.contents[e.getId()];
		if(dm == null || dm.size == 0)
			return "";
		try(Resources r = Resources.get()) {
			StringBuilder sb = r.sb();
			content.readText(dm, r.buffer(), r.chars(), r.decoder(), sb);
			return sb.toString();
		}
		
	}
}
