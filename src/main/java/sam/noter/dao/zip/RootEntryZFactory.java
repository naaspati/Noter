package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.charset.CodingErrorAction.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.di.ConfigManager;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.StringResources;
import sam.noter.dao.RootEntryFactory;
import sam.noter.dao.api.IRootEntry;
import sam.string.StringSplitIterator;

@Singleton
public class RootEntryZFactory implements RootEntryFactory, AutoCloseable {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	private final Logger logger;
	private final Path mydir;
	private final TextInFile index;
	private final TextInFile content;
	private final MetaHandler metas;

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

		this.metas = new MetaHandler(meta);
		this.content = new TextInFile(content, !b);
		this.index = new TextInFile(index, !b);

	}

	@Override
	public void close() throws Exception {
		metas.close();

	}

	@Override
	public RootEntryZ create(Path path) throws Exception {
		return new RootEntryZ(cacheFile(path));
	}
	@Override
	public RootEntryZ load(Path file) throws Exception {
		return new RootEntryZ(cacheFile(file));
	}
	private CacheDir cacheFile(Path file) throws IOException {
		file =  file.normalize().toAbsolutePath();
		String str = map.get(file);
		Path cache;
		if(str == null) { 
			cache = newCacheDir(file);
			map.put(cache, cache.getFileName().toString());
		} else {
			cache = temp_dir.resolve(str);
		}
		CacheDir d = new CacheDir(file, cache);
		return d;
	}
	private Path newCacheDir(Path file) throws IOException {
		String s = "-"+file.getFileName().toString(); 
		Path p = temp_dir.resolve(System.currentTimeMillis()+s);
		while(Files.exists(p))
			p = temp_dir.resolve(System.currentTimeMillis()+"-"+(int)(Math.random()*100)+s);
		return p;
	}

	public void close(RootEntryZ root) {
		// TODO Auto-generated method stub
		
	}

	public EntryZ[] getEntries(RootEntryZ root, EntryZ[] sink) {
		// TODO Auto-generated method stub
		return null;
	}

	public void save(RootEntryZ root, Path file) {
		// TODO Auto-generated method stub
		
	}

	public String readContent(RootEntryZ rootEntryZ, EntryZ e) {
		// TODO Auto-generated method stub
		return null;
	}


}
