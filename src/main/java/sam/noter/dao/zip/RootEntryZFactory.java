package sam.noter.dao.zip;

import static sam.noter.Utils.TEMP_DIR;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import sam.collection.Iterables;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.noter.dao.RootEntry;
import sam.noter.dao.RootEntryFactory;
public class RootEntryZFactory implements RootEntryFactory {
	private static volatile RootEntryZFactory INSTANCE;
	private static final Logger LOGGER = MyLoggerFactory.logger(RootEntryZFactory.class);

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

	class PathToCacheDir {
		private final Map<String, String> map = new HashMap<>();
		private final Path config_file = temp_dir.resolve(temp_dir.getFileName());

		public PathToCacheDir() throws IOException {
			if(Files.notExists(config_file))
				return;

			Files.lines(config_file)
			.forEach(s -> {
				int index = s.indexOf('\t');
				if(index < 0) 
					return;
				String path = s.substring(0, index); 
				String name = s.substring(index+1);

				map.put(path, name);
			});

			if(!map.isEmpty() && LocalDate.now().getDayOfWeek() == DayOfWeek.SUNDAY){
				ArrayList<String> remove = new ArrayList<>();

				map.forEach((path, name ) -> {
					if(!Files.isRegularFile(Paths.get(path))) {
						Path p = temp_dir.resolve(name);
						if(Files.exists(p))
							Util.hide(() -> FilesUtilsIO.deleteDir(p));

						remove.add(path);
					}
				});
				if(!remove.isEmpty()) {
					map.keySet().removeAll(remove);
					Files.write(config_file, Iterables.map(map.entrySet(), e -> e.getKey()+"\t"+e.getValue()), StandardOpenOption.TRUNCATE_EXISTING);	
				}
			} 
		}
		public void put(CacheDir dir) {
			String name = dir.root.getFileName().toString();
			String path = dir.getSourceFile().toString();
			map.put(path, name);
			Util.hide(() -> StringWriter2.appendText(config_file, path+"\t"+name+"\n"));
		}
		public String get(Path path) {
			if(path == null) return null;
			return map.get(path.toString());
		}
	}
	private final Path temp_dir = TEMP_DIR.resolve(RootEntryZFactory.class.getName());
	private final PathToCacheDir pathToCacheDir; 

	private RootEntryZFactory() throws IOException {
		if(Files.notExists(temp_dir)) {
			Files.createDirectories(temp_dir);
			//TODO	Files.setAttribute(temp_dir, "dos:hidden", true);
			LOGGER.fine(() -> "DIR CREATED: "+temp_dir);
		}
		pathToCacheDir = new PathToCacheDir();
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
		String str = pathToCacheDir.get(file);
		CacheDir d = new CacheDir(file, str == null ? newCacheDir(file) : temp_dir.resolve(str), pathToCacheDir);
		return d;
	}
	private Path newCacheDir(Path file) throws IOException {
		String s = "-"+file.getFileName().toString(); 
		Path p = temp_dir.resolve(System.currentTimeMillis()+s);
		while(Files.exists(p))
			p = temp_dir.resolve(System.currentTimeMillis()+"-"+(int)(Math.random()*100)+s);
		return p;
	}

	/*
	 * TODO public RootEntryZ convert(RootEntry root) throws Exception {
		RootEntryZ t = (RootEntryZ) create();
		t.setItems(((Entry)root).getChildren()
				.stream()
				.map(this::map).collect(Collectors.toList()));
		t.setModified();
		return t;
	}
	 * private EntryZ map(TreeItem<String> item) {
		EntryZ e = new EntryZ(((Entry)item).getId(), (Entry)item);
		e.setItems(item.getChildren().stream().map(this::map).collect(Collectors.toList()));
		return e;
	}
	 */
	
}
