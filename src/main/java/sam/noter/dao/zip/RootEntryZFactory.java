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

	private class PathToCacheDir {
		private final Map<Path, String> map;
		private final Path config_file = Utils.APP_DATA.resolve(RootEntryZFactory.class.getName());

		public PathToCacheDir() throws IOException {
			map = Files.notExists(config_file) ? new HashMap<>() : Files.lines(config_file).filter(s -> !MyUtilsCheck.isEmptyTrimmed(s)).collect(Collectors.toMap(s -> Paths.get(s.substring(s.indexOf(' ')+1)), s -> s.substring(0, s.indexOf(' ')), (o, n) -> n));

			if(!map.isEmpty()) {
				ArrayList<Path> remove = new ArrayList<>();
				map.forEach((f, s) -> {
					if(!Files.isRegularFile(f)) {
						FilesUtilsIO.delete(temp_dir.resolve(s).toFile());
						remove.add(f);
						LOGGER.fine(() -> "REMOVED CACHED: "+temp_dir.resolve(s));
					}
				});
				if(!remove.isEmpty()) {
					map.keySet().removeAll(remove);
					Files.write(config_file, Iterables.map(map.entrySet(), e -> e.getValue()+" "+e.getKey()), StandardOpenOption.TRUNCATE_EXISTING);
				}
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
	private final Path temp_dir = Utils.APP_DATA.resolve("java_temp").resolve(RootEntryZFactory.class.getName());
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
	public RootEntry create() throws Exception {
		return new RootEntryZ(cacheFile(null));
	}
	@Override
	public RootEntry load(File file) throws Exception {
		return new RootEntryZ(cacheFile(file));
	}

	private CacheDir cacheFile(File file) throws IOException {
		Path path =  file == null ? null : file.toPath().normalize().toAbsolutePath();
		String str = pathToCacheDir.map.get(path);

		if(str == null) {
			CacheDir dir = new CacheDir(path, newCacheDir());
			if(file != null && file.exists())
				unzip(file, dir);
			return dir;
		} else {
			Path cacheDirPath = temp_dir.resolve(str);
			CacheDir cacheDir = new CacheDir(path, cacheDirPath);
			Path lm = cacheDir.lastModified();

			if(Files.exists(lm) && (!file.exists() || file.lastModified() != LongSerializer.read(lm))) {
				LOGGER.fine(() -> "DELETE cacheDir: "+cacheDirPath);
				FilesUtilsIO.deleteDir(cacheDirPath);
				Files.deleteIfExists(cacheDirPath); //needed 
			}
			
			if(Files.notExists(lm))
				unzip(file, cacheDir);
			else
				LOGGER.fine(() -> "CACHE LOADED: "+cacheDir);

			return  cacheDir;
		}
	}

	private final WeakAndLazy<byte[]> wbuffer = new WeakAndLazy<>(() -> new byte[BufferSize.DEFAULT_BUFFER_SIZE]);

	private void unzip(File file, CacheDir cacheDir) throws FileNotFoundException, IOException {
		Path cache = cacheDir.cacheDir;
		Path content = cacheDir.contentDir;
		Files.createDirectories(content);

		synchronized (wbuffer) {
			try(InputStream is = new FileInputStream(file);
					ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8) ) {

				byte[] buffer = wbuffer.get();
				ZipEntry z = null;

				while((z = zis.getNextEntry()) != null) {
					try(OutputStream out = Files.newOutputStream(cache.resolve(z.getName()))) {
						int n = 0;
						while((n = zis.read(buffer)) > 0) {
							out.write(buffer, 0, n);
						}
					}
				}
			}
			LOGGER.fine(() -> "UNZIPPED: "+file+" -> "+cacheDir);
			saveLastModified(file, cacheDir);
			pathToCacheDir.put(cacheDir);
		}
	}
	private void saveLastModified(File file, CacheDir cacheDir) throws IOException {
		LongSerializer.write(file.lastModified(), cacheDir.lastModified());
	}
	private void zip(CacheDir cacheDir, Path target) throws IOException {
		Path temp = Files.createTempFile(target.getFileName().toString(), null);

		synchronized (wbuffer) {
			try(OutputStream os = Files.newOutputStream(temp);
					ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {

				Path index = cacheDir.indexPath();
				if(Files.notExists(index))
					return;

				byte[] buffer = wbuffer.get();
				ZipEntry e = new ZipEntry("index");
				zos.putNextEntry(e);
				write(buffer, index, zos);

				Path content = cacheDir.contentDir;
				if(Files.notExists(content)) return;

				String[] contents = content.toFile().list();
				if(contents == null || contents.length == 0) return;
				for (String f : contents) {
					e = new ZipEntry("content/"+f);
					zos.putNextEntry(e);

					write(buffer, content.resolve(f), zos);
				}
			}
		}
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		LOGGER.info("MOVED: "+temp+ "  "+target);
		saveLastModified(target.toFile(), cacheDir);
	}

	private static void write(byte[] buffer, Path file, ZipOutputStream zos) throws IOException {
		try(InputStream is = Files.newInputStream(file)) {
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
		EntryZ entry;

		public Temp(int parent_id, int order, EntryZ e) {
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
		}
		public Path lastModified() {
			return cacheDir.resolve("lastmodified");
		}
		public Path getSourceFile() {
			return currentFile;
		}
		public void setSourceFile(Path path) {
			currentFile = path;
		}
		public List<EntryZ> loadEntries(RootEntryZ root) throws IOException {
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

						Temp  t = new Temp(parent_id, order, new EntryZ(root, id, lastModified, title));
						map.put(id, t);
						maxId = Math.max(maxId, id);
						lines.put(id, s);
						return  t;
					})
					.collect(Collectors.groupingBy(t -> t.parent_id));

			Comparator<Temp> comparator = (e,f) -> Integer.compare(e.order, f.order);
			ArrayList<EntryZ> temp = new ArrayList<>();

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
		private Path contentPath(EntryZ e) {
			return contentDir.resolve(String.valueOf(e.getId()));
		}
		public String getContent(EntryZ e) throws IOException {
			Path p = contentPath(e);
			return Files.notExists(p) ? null : StringReader2.getText(p);
		}
		public void save(RootEntryZ root, File file) throws IOException {
			if(!root.isModified()) return;
			StringBuilder sb = new StringBuilder(sbsize + 200);

			walk(root, sb);
			StringWriter2.setText(indexPath(), sb.toString());
			zip(this, file.toPath());
			this.currentFile = file.toPath();
			pathToCacheDir.put(this);
		}
		private void walk(EntryZ entry, StringBuilder sb) throws IOException {
			@SuppressWarnings("rawtypes")
			List list = entry.getChildren();
			if(list.isEmpty()) return;

			int parentId = entry.getId();

			int order = 0;
			boolean cM = entry.isChildrenModified();

			for (Object ti : list) {
				EntryZ e = (EntryZ) ti;
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
		private void logModification(EntryZ e) {
			LOGGER.info(() -> {
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
					
					return sb.toString();
				}
			});
		}
		@Override
		public String toString() {
			return "CacheDir [currentFile=" + currentFile + ", cacheDir=" + cacheDir + "]";
		}
		public EntryZ newEntry(String title, RootEntryZ root) {
			return new EntryZ(root, ++maxId, title, true);
		}
		public EntryZ newEntry(EntryZ d, RootEntryZ root) {
			Path src = d.getRoot().getCacheDir().contentPath(d);
			EntryZ nnew = new EntryZ(root, ++maxId, d.getTitle(), true);
			nnew.setLastModified(d.getLastModified());
			
			if(Files.exists(src)) {
				try {
					Files.move(src, contentPath(nnew), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} 
			return nnew;
		}
	}
	public RootEntryZ convert(RootEntry root) throws Exception {
		RootEntryZ t = (RootEntryZ) create();
		t.setItems(((Entry)root).getChildren()
				.stream()
				.map(this::map).collect(Collectors.toList()));
		t.setModified();
		return t;
	}
	private EntryZ map(TreeItem<String> item) {
		EntryZ e = new EntryZ(((Entry)item).getId(), (Entry)item);
		e.setItems(item.getChildren().stream().map(this::map).collect(Collectors.toList()));
		return e;
	}
}
