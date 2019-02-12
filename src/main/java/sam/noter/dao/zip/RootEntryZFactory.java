package sam.noter.dao.zip;

import static sam.noter.Utils.TEMP_DIR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.noter.dao.RootEntry;
import sam.noter.dao.RootEntryFactory;
public class RootEntryZFactory implements RootEntryFactory {
	private static volatile RootEntryZFactory INSTANCE;
	private static final Logger logger = LogManager.getLogger(RootEntryZFactory.class);

	public static RootEntryZFactory getInstance() throws IOException {
		if (INSTANCE != null)
			return INSTANCE;

		synchronized (RootEntryZFactory.class) {
			if (INSTANCE != null)
				return INSTANCE;

			INSTANCE = new RootEntryZFactory();
			return INSTANCE;
		}
	}

	private final Path temp_dir = TEMP_DIR.resolve(RootEntryZFactory.class.getSimpleName());
	// path -> cacheDir dirName
	private Map<Path, String> map = new HashMap<>();
	private final Path path = temp_dir.resolve("pathCacheDirnameMap"); 
	private int mod = 0;

	private RootEntryZFactory() throws IOException {
		if(Files.notExists(temp_dir)) {
			Files.createDirectories(temp_dir);
			logger.debug("DIR CREATED: {}", temp_dir);
		}
		if(Files.exists(path)) {
			Files.lines(path).forEach(s -> {
				int n = s.indexOf('\t');
				if(n > 0)
					map.put(Paths.get(s.substring(0, n)), s.substring(n+1));
			});
		}
	}

	@Override
	public RootEntry create(Path path) throws Exception {
		return new RootEntryZ(cacheFile(path));
	}
	@Override
	public RootEntry load(Path file) throws Exception {
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


}
