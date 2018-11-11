package sam.noter.dao.zip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javafx.scene.control.TreeItem;
import sam.collection.Iterables;
import sam.io.BufferSize;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsCheck;
import sam.noter.Utils;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;
import sam.noter.dao.RootEntryFactory;
import sam.reference.WeakAndLazy;
import sam.string.StringUtils.StringSplitIterator;

public class RootEntryGFactory implements RootEntryFactory {
	private static volatile RootEntryGFactory INSTANCE;
	private static final Logger LOGGER = MyLoggerFactory.logger(RootEntryGFactory.class);

	public static RootEntryGFactory getInstance() throws IOException {
		if (INSTANCE != null)
			return INSTANCE;

		synchronized (RootEntryGFactory.class) {
			if (INSTANCE != null)
				return INSTANCE;

			INSTANCE = new RootEntryGFactory();
			return INSTANCE;
		}
	}
	
	private class PathToCacheDir {
		private final Map<Path, String> map;
		private final Path config_file = Utils.APP_DATA.resolve(RootEntryGFactory.class.getName());
		
		public PathToCacheDir() throws IOException {
			map = Files.notExists(config_file) ? new HashMap<>() : Files.lines(config_file).filter(s -> !MyUtilsCheck.isEmptyTrimmed(s)).collect(Collectors.toMap(s -> Paths.get(s.substring(s.indexOf(' ')+1)), s -> s.substring(0, s.indexOf(' ')), (o, n) -> n));
			
			if(!map.isEmpty()) {
				int size = map.size();
				map.keySet().removeIf(f -> !Files.isRegularFile(f));
				if(size != map.size()) 
					Files.write(config_file, Iterables.map(map.entrySet(), e -> e.getValue()+" "+e.getKey()), StandardOpenOption.TRUNCATE_EXISTING);
			}
		}
		public void put(CacheDir dir) {
			String str = dir.cacheDir.getFileName().toString();
			map.put(dir.currentFile, str);
			try {
				StringWriter2.appendText(config_file, str+" "+dir.currentFile+"\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	private final Path temp_dir = Utils.APP_DATA.resolve("java_temp").resolve(RootEntryGFactory.class.getName());
	private final PathToCacheDir pathToCacheDir; 
	private static final String CONTENT = "content"; 

	private RootEntryGFactory() throws IOException {
		if(Files.notExists(temp_dir)) {
			Files.createDirectories(temp_dir);
		//TODO	Files.setAttribute(temp_dir, "dos:hidden", true);
			LOGGER.fine(() -> "DIR CREATED: "+temp_dir);
		}
		pathToCacheDir = new PathToCacheDir();
	}
	@Override
	public RootEntry create() throws Exception {
		return new RootEntryG(cacheFile(null));
	}
	@Override
	public RootEntry load(File file) throws Exception {
		return new RootEntryG(cacheFile(file));
	}

	private CacheDir cacheFile(File file) throws IOException {
		Path path =  file == null ? null : file.toPath().normalize().toAbsolutePath();
		String str = pathToCacheDir.map.get(path);

		if(str == null) 
			return new CacheDir(path, newCacheDir()); 
		else {
			Path cacheDir = temp_dir.resolve(str);
			boolean b = file == null || !file.exists();

			if(Files.exists(cacheDir)) {
				Path lastmodified = cacheDir.resolve("lastmodified");

				if(b || Files.notExists(lastmodified) || file.lastModified() != LongSerializer.read(lastmodified)) {
					LOGGER.fine(() -> "DELETE cacheDir: "+cacheDir);
					FilesUtilsIO.deleteDir(cacheDir);
				} else {
					System.out.println("using cacheDir: "+cacheDir);
				}	
			}

			CacheDir cachefile = new CacheDir(path, cacheDir);

			if(!b && Files.notExists(cacheDir))
				unzip(file, cacheDir);
			else 
				LOGGER.fine(() -> "CACHE LOADED: "+cachefile);

			return cachefile;
		}
	}

	private static final WeakAndLazy<byte[]> wbuffer = new WeakAndLazy<>(() -> new byte[BufferSize.DEFAULT_BUFFER_SIZE]);

	private static void unzip(File file, Path cacheDir) throws FileNotFoundException, IOException {
		Files.createDirectories(cacheDir.resolve(CONTENT));

		synchronized (wbuffer) {
			try(InputStream is = new FileInputStream(file);
					ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8) ) {

				byte[] buffer = wbuffer.get();
				ZipEntry z = null;

				while((z = zis.getNextEntry()) != null) {
					try(OutputStream out = Files.newOutputStream(cacheDir.resolve(z.getName()))) {
						int n = 0;
						while((n = zis.read(buffer)) > 0) {
							out.write(buffer, 0, n);
						}
					}
				}
			}
			LOGGER.fine(() -> "UNZIPPED: "+file+" -> "+cacheDir);
			saveLastModified(file, cacheDir);
		}
	}
	private static void saveLastModified(File file, Path cacheDir) throws IOException {
		LongSerializer.write(file.lastModified(), cacheDir.resolve("lastmodified"));
	}
	private static void zip(Path cacheDir, Path target) throws IOException {
		Path temp = Files.createTempFile(target.getFileName().toString(), null);
		File cache = cacheDir.toFile();

		synchronized (wbuffer) {
			try(OutputStream os = Files.newOutputStream(temp);
					ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {

				File file = new File(cache, "index");
				if(!file.exists())
					return;

				byte[] buffer = wbuffer.get();
				ZipEntry e = new ZipEntry("index");
				zos.putNextEntry(e);
				write(buffer, file, zos);

				File content = new File(cache, CONTENT);
				if(!content.exists()) return;

				String[] contents = content.list();
				if(contents == null || contents.length == 0) return;
				for (String f : contents) {
					e = new ZipEntry("content/"+f);
					zos.putNextEntry(e);

					write(buffer, new File(content, f), zos);
				}
			}
		}
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("MOVED: "+temp+ "  "+target);
		saveLastModified(target.toFile(), cacheDir);
	}

	private static void write(byte[] buffer, File file, ZipOutputStream zos) throws IOException {
		try(FileInputStream is = new FileInputStream(file);) {
			int n = 0;
			while((n = is.read(buffer)) > 0)
				zos.write(buffer, 0, n);
			zos.closeEntry();
		}
	}
	private Path newCacheDir() throws IOException {
		Path p = temp_dir.resolve(String.valueOf(System.currentTimeMillis()));
		while(Files.exists(p))
			p = temp_dir.resolve(String.valueOf(System.currentTimeMillis()+(int)(Math.random()*100)));
		Files.createDirectories(p);
		return p;
	}

	private class Temp {
		int parent_id;
		int order;
		EntryG entry;

		public Temp(int parent_id, int order, EntryG e) {
			this.parent_id = parent_id;
			this.order = order;
			this.entry = e;
		}
	}
	
	private static final WeakAndLazy<StringBuilder> logSB = new WeakAndLazy<>(StringBuilder::new);

	class CacheDir {
		Path startFile;
		private Path currentFile;
		private final Path cacheDir;
		private final Path contentDir;
		private int sbsize = 0;
		private int maxId;
		private Map<Integer, String> lines = new HashMap<>(); 

		public CacheDir(Path path, Path cacheDir) throws IOException {
			this.startFile = path;
			this.currentFile = path;
			this.cacheDir = cacheDir;
			this.contentDir = cacheDir.resolve("content");
			Files.createDirectories(contentDir);
		}
		public Path getSourceFile() {
			return currentFile;
		}
		public void setSourceFile(Path path) {
			currentFile = path;
		}
		public List<EntryG> loadEntries() throws IOException {
			Path p = indexPath();
			if(Files.notExists(p)) return new ArrayList<>();
			HashMap<Integer, Temp> map = new HashMap<>();

			Map<Integer, List<Temp>> grouped = Files.lines(p)
					.map(s -> {
						sbsize += s.length();

						Iterator<String> iter = new StringSplitIterator(s, ' ', 5);
						int id = Integer.parseInt(iter.next());
						int parent_id = Integer.parseInt(iter.next());
						int order = Integer.parseInt(iter.next());
						long lastModified = Long.parseLong(iter.next());
						String title = iter.next();

						Temp  t = new Temp(parent_id, order, new EntryG(this, id, lastModified, title));
						map.put(id, t);
						maxId = Math.max(maxId, id);
						lines.put(id, s);
						return  t;
					})
					.collect(Collectors.groupingBy(t -> t.parent_id));

			Comparator<Temp> comparator = (e,f) -> Integer.compare(e.order, f.order);
			ArrayList<EntryG> temp = new ArrayList<>();

			grouped.forEach((parent_id, list) -> {
				if(parent_id == RootEntry.ROOT_ENTRY_ID) return;
				
				Temp t = map.get(parent_id);
				list.sort(comparator);
				temp.clear();
				list.forEach(x -> temp.add(x.entry));
				t.entry.setItems(temp);
			});
			temp.clear();
			Optional.ofNullable(grouped.get(RootEntry.ROOT_ENTRY_ID))
			.ifPresent(list -> list.forEach(x -> temp.add(x.entry)));
			return temp;
		}
		private Path indexPath() {
			return cacheDir.resolve("index");
		}
		private Path contentPath(EntryG e) {
			return contentDir.resolve(String.valueOf(e.getId()));
		}
		public String getContent(EntryG e) throws IOException {
			Path p = contentPath(e);
			return Files.notExists(p) ? null : StringReader2.getText(p);
		}
		public void save(RootEntryG root, File file) throws IOException {
			if(!root.isModified()) return;
			StringBuilder sb = new StringBuilder(sbsize + 200);

			walk(root, sb);
			StringWriter2.setText(cacheDir.resolve("index"), sb.toString());
			zip(cacheDir, file.toPath());
			this.currentFile = file.toPath();
			pathToCacheDir.put(this);
		}
		private void walk(EntryG entry, StringBuilder sb) throws IOException {
			@SuppressWarnings("rawtypes")
			List list = entry.getChildren();
			if(list.isEmpty()) return;

			int parentId = entry.getId();

			int order = 0;
			boolean cM = entry.isChildrenModified();

			for (Object ti : list) {
				EntryG e = (EntryG) ti;
				if(!e.isModified() && !cM) {
					sb.append(lines.get(e.getId())) ;
				} else {
					sb.append(e.getId()).append(' ')
					.append(parentId).append(' ')
					.append(order++).append(' ')
					.append(e.getLastModified()).append(' ')
					.append(e.getTitle()).append('\n');

					if(e.isContentModified()) 
						StringWriter2.setText(contentPath(e), notNull(e.getContent()));
					logModification(e);
				}
				walk(e, sb);
			}
		}
		private String notNull(String content) {
			return content == null ? "" : content;
		}
		private void logModification(EntryG e) {
			synchronized(logSB) {
				StringBuilder sb = logSB.get();
				sb.setLength(0);
				
				sb.append(e).append(" [");
				if(e.isTitleModified())
					sb.append("TITLE, ");
				if(e.isContentModified())
					sb.append("CONTENT, ");
				if(e.isChildrenModified())
					sb.append("CHILDREN, ");
				sb.append(']');

				String s = sb.toString();
				if(LOGGER.isLoggable(Level.FINE))
					LOGGER.fine(s);
				else
					System.out.println(s);
			}
		}
	}
	public RootEntryG convert(RootEntry root) throws Exception {
		RootEntryG t = (RootEntryG) create();
		t.setItems(((Entry)root).getChildren()
		.stream()
		.map(this::map).collect(Collectors.toList()));
		t.setModified();
		return t;
	}
	private EntryG map(TreeItem<String> item) {
		EntryG e = new EntryG(((Entry)item).getId(), (Entry)item);
		e.setItems(item.getChildren().stream().map(this::map).collect(Collectors.toList()));
		return e;
	}
}
