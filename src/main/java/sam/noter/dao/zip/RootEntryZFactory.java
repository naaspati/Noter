package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import sam.collection.CollectionUtils;
import sam.di.AppConfig;
import sam.io.fileutils.FilesUtilsIO;
import sam.myutils.Checker;
import sam.myutils.ThrowException;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Junk;
import sam.noter.Utils;
import sam.noter.dao.RootEntryFactory;

@Singleton
public class RootEntryZFactory implements RootEntryFactory {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{singleton.init();}

	private final Logger logger = Utils.logger(getClass());
	private final Path mydir;
	private final Path metasPath;
	private final ArrayList<MetaImpl> metas = new ArrayList<>();
	private final IdentityHashMap<Meta, CachedRoot> active = new IdentityHashMap<>();

	@Inject
	public RootEntryZFactory(AppConfig config) throws IOException {
		this.mydir = config.tempDir().resolve(getClass().getName());
		this.metasPath = mydir.resolve("app.index");

		if(Files.notExists(metasPath)) 
			FilesUtilsIO.deleteDir(mydir);

		Files.createDirectories(mydir);
		if(Files.exists(metasPath)) 
			MetaHelper.read(metasPath, MetaImpl::new, t -> metas.add((MetaImpl) t));
	}

	@Override
	public RootEntryZ create(Path path) throws Exception {
		if(Files.exists(path))
			throw new IOException("file already exists: "+path);

		return create0(path.normalize().toAbsolutePath());
	}
	private RootEntryZ create0(Path path) throws Exception {
		//TODO
		// metas.set(0, cacheImpl);
		return Junk.notYetImplemented(); // return new RootEntryZ(m, path, this);
	}
	
	public  class MetaImpl implements Meta {
		public final int id;
		private long lastModified;
		private Path source;
		
		public MetaImpl(int id, long lasModified) {
			this.id = id;
			this.lastModified = lasModified;
		}
		public MetaImpl(int id, long lasModified, Path jbook_path) {
			this(id, lasModified);
			this.source = Objects.requireNonNull(jbook_path);
		}
		@Override
		public int getId() {
			return id;
		}
		@Override
		public Path source() {
			return source;
		}
		@Override
		public long getLastModified() {
			return lastModified;
		}
		private void updateLastModified() {
			this.lastModified = source.toFile().lastModified();
		}
	}

	@Override
	public RootEntryZ load(Path path) throws Exception {
		if(!Files.isRegularFile(path))
			throw new IOException("file not found: "+path);

		path =  path.normalize().toAbsolutePath();
		int index = find(path);

		if(index < 0)
			return create0(path);

		MetaImpl m = metas.get(index);

		if(active.get(m) != null)
			ThrowException.illegalAccessError();

		if(m.lastModified != path.toFile().lastModified()) {
			logger.debug("RESET CACHE: because: meta.lastModified({}) != path.toFile().lastModified({}),  path: {}", m.lastModified, path.toFile().lastModified(), path);
			return create0(path);
		}
		return new CacheImpl(m);
	}

	private class CacheImpl extends CachedRoot {
		private MetaImpl meta;

		public CacheImpl(MetaImpl meta) throws IOException {
			super(resolve(meta.id+".index"), resolve(meta.id+".content"));
			this.meta = meta;
			active.put(meta, this);
		}

		@SuppressWarnings({"unchecked", "rawtypes"})
		@Override
		protected void saveMeta() throws IOException {
			Objects.requireNonNull(meta);
			
			if(metas.isEmpty())
				metas.add(meta);
			else if(metas.get(0) != meta) {
				metas.remove(meta);
				metas.add(0, meta);
			}
			List list = metas;
			MetaHelper.write(list, metasPath);
		}

		@Override
		public Path getJbookPath() {
			return meta.source();
		}
		@Override
		protected void updateLastModified() {
			meta.updateLastModified();
		}

		@Override
		public void close() throws IOException {
			super.close();
			active.remove(meta);
			meta = null;
		}
	}

	private int find(Path path) {
		for (int i = 0; i < metas.size(); i++) {
			if(path.equals(metas.get(i).source()))
				return i;
		}
		return -1;
	}
	
	public Path resolve(String s) {
		return mydir.resolve(s);
	}

	@Override
	public List<Path> recentsFiles() {
		if(metas.isEmpty())
			return Collections.emptyList();
		else if(metas.size() == 1)
			return Collections.singletonList(metas.get(0).source());
		else {
			Path[] array = new Path[metas.size()];
			for (int i = 0; i < array.length; i++)
				array[i] = metas.get(i).source();

			return Arrays.asList(array);	
		}
	}
}
