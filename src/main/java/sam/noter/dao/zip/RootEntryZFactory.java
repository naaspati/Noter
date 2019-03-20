package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import sam.di.AppConfig;
import sam.io.fileutils.FilesUtilsIO;
import sam.myutils.ThrowException;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Junk;
import sam.noter.Utils;
import sam.noter.dao.RootEntryFactory;

@Singleton
public class RootEntryZFactory implements RootEntryFactory {
	private static final EnsureSingleton singleton = new EnsureSingleton();

	private final Logger logger;
	private final Path mydir;
	private final Path metasPath;
	private final ArrayList<Meta> metas;

	@Inject
	public RootEntryZFactory(AppConfig config) throws IOException {
		singleton.init();

		this.logger = Utils.logger(getClass());
		this.mydir = config.tempDir().resolve(getClass().getName());
		this.metasPath = mydir.resolve("app.index");
		this.metas = new ArrayList<>();

		if(Files.notExists(metasPath)) 
			FilesUtilsIO.deleteDir(mydir);

		Files.createDirectories(mydir);
		if(Files.exists(metasPath))
			MetaHelper.read(metas, metasPath);

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

	@Override
	public RootEntryZ load(Path path) throws Exception {
		if(!Files.isRegularFile(path))
			throw new IOException("file not found: "+path);

		path =  path.normalize().toAbsolutePath();
		int index = find(path);

		if(index < 0)
			return create0(path);

		Meta m = metas.get(index);
		
		if(m instanceof Cache)
			ThrowException.illegalAccessError();
		
		if(m.lastModified() != path.toFile().lastModified()) {
			logger.debug("RESET CACHE: because: meta.lastModified({}) != path.toFile().lastModified({}),  path: {}", m.lastModified(), path.toFile().lastModified(), path);
			return create0(path);
		}
		
		CacheImpl c = new CacheImpl(m, mydir);
		metas.remove(index);
		metas.set(0, c);
		return new RootEntryZ(c);
	}
	
	private class CacheImpl extends Cache {
		public CacheImpl(Meta meta, Path saveDir) throws IOException {
			super(meta, saveDir);
		}

		@Override
		protected void saveMeta() throws IOException {
			MetaHelper.write(metas, metasPath);
		}
	}
	
	private int find(Path path) {
		for (int i = 0; i < metas.size(); i++) {
			if(path.equals(metas.get(i).source()))
				return i;
		}
		return -1;
	}
	@Override
	public List<Path> recentsFiles() {
		Path[] array = new Path[metas.size()];
		for (int i = 0; i < array.length; i++)
			array[i] = metas.get(i).source();
		
		return Arrays.asList(array);
	}
}
