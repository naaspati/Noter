package sam.noter.dao.zip;

import static sam.myutils.MyUtilsCheck.*;
import static sam.myutils.MyUtilsCheck.notExists;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

import sam.io.BufferSize;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.IntSerializer;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsCheck;
import sam.noter.dao.RootEntry;
import sam.noter.dao.zip.RootEntryZFactory.PathToCacheDir;
import sam.reference.ReferenceUtils;
import sam.reference.WeakAndLazy;
import sam.string.StringUtils.StringSplitIterator;

class CacheDir implements AutoCloseable {
	private static final Logger LOGGER = MyLoggerFactory.logger(CacheDir.class);
	private static final WeakAndLazy<byte[]> wbuffer = new WeakAndLazy<>(() -> new byte[BufferSize.DEFAULT_BUFFER_SIZE]);

	Path startFile;
	private Path currentFile;
	public final Path root;
	public final Path contentDir;
	private int maxId;
	private WeakReference<Map<Integer, String>> lines;
	private Path removedDir;
	private final PathToCacheDir pathToCacheDir; 

	public CacheDir(Path path, Path cacheDir, PathToCacheDir pathToCacheDir) throws IOException {
		this.startFile = path;
		this.currentFile = path;
		this.root = cacheDir;
		this.contentDir = cacheDir.resolve("content");
		this.pathToCacheDir = pathToCacheDir;
		this.removedDir = this.root.resolve("removed");
		prepareCache();

	}
	private Path maxId() { return root.resolve("maxId"); }
	private Path index2() { return root.resolve("index2"); }	
	private Path lastModified() { return root.resolve("lastmodified"); }
	private Path index() { return this.root.resolve("index"); }

	public Path getSourceFile() {
		return currentFile;
	}
	public void setSourceFile(Path path) {
		currentFile = path;
	}
	public void loadEntries(RootEntryZ root) throws IOException, ClassNotFoundException {
		Path index = index2();
		if(Files.exists(index)) {
			Path p = index;
			LOGGER.fine(() -> "index loaded: "+p);
			maxId = IntSerializer.read(maxId()); 
			root.setItems(ObjectReader.read(index, dis -> EntryZ.read(dis, root)).getChildren());
			return;
		}
		index = index();
		if(Files.notExists(index)) return;

		HashMap<Integer, Temp> map = new HashMap<>();
		HashMap<Integer, String> lines = new HashMap<>();

		Map<Integer, List<Temp>> grouped = Files.lines(index)
				.map(s -> {
					Iterator<String> iter = new StringSplitIterator(s, ' ', 5);
					int id = Integer.parseInt(iter.next());
					int parent_id = Integer.parseInt(iter.next());
					int order = Integer.parseInt(iter.next());
					long lastModified = Long.parseLong(iter.next());
					String title = iter.next();

					Temp  t = new Temp(parent_id, order, new EntryZ(root, id, lastModified, title));
					maxId = Math.max(maxId, id);
					map.put(id, t);
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

		root.setItems(temp);
		this.lines = new WeakReference<Map<Integer,String>>(lines);
		saveRoot(root);
	}
	private Path contentPath(EntryZ e) {
		return contentDir.resolve(String.valueOf(e.getId()));
	}
	public String getContent(EntryZ e) throws IOException {
		Path p = contentPath(e);
		if(Files.notExists(p))
			return null;
		String  s = StringReader2.getText(p);
		LOGGER.fine(() -> "CONTENT LOADED: "+e);
		return s;
	}
	public void save(RootEntryZ root, Path file) throws IOException {
		if(!root.isModified()) return;

		StringBuilder sv2 = new StringBuilder(100);
		ArrayList<String> list = new ArrayList<>(); 
		walk(root, list, sv2);
		Files.write(index(), list, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		saveRoot(root);
		zip(file);
		this.currentFile = file;
		pathToCacheDir.put(this);
	}

	private void saveRoot(RootEntryZ root) throws IOException {
		IntSerializer.write(maxId, maxId());
		ObjectWriter.write(index2(), root, RootEntryZ::write);
		LOGGER.fine(() -> "CREATED: "+maxId()+" (maxId: "+maxId+")");
		LOGGER.fine(() -> "CREATED: "+index2());
	}

	private void walk(EntryZ entry, List<String> sink, StringBuilder sb) throws IOException {
		@SuppressWarnings("rawtypes")
		List list = entry.getChildren();
		if(list.isEmpty()) return;

		int parentId = entry.getId();

		int order = 0;
		boolean cM = entry.isChildrenModified();
		Map<Integer, String> lines = ReferenceUtils.get(this.lines);
		if(lines == null) {
			Path index = index();
			if(Files.notExists(index))
				lines = new HashMap<>();
			else {
				lines = Files.lines(index).collect(Collectors.toMap(s -> Integer.parseInt(s.substring(0, s.indexOf(' '))), s -> s));
				LOGGER.fine(() -> "loaded for lines: "+index);
			}
		}
		for (Object ti : list) {
			EntryZ e = (EntryZ) ti;
			String line = lines.get(e.id);
			if(line != null && !e.isModified() && !cM) {
				sink.add(line);
			} else {
				sb.setLength(0);
				sb.append(e.id).append(' ')
				.append(parentId).append(' ')
				.append(order++).append(' ')
				.append(e.getLastModified()).append(' ')
				.append(e.getTitle());

				line = sb.toString();
				lines.put(e.id, line);
				sink.add(line);

				if(e.isModified()) {
					if(e.isContentModified()) {
						String s = e.getContent();
						Path p = contentPath(e);
						if(isEmpty(s)) {
							if(Files.deleteIfExists(p))
								LOGGER.fine(() -> "DELETED: "+p);
						} else
							StringWriter2.setText(p, s);
					}
					logModification(e, sb);
				}
			}
			walk(e, sink, sb);
		}
	}
	private void logModification(EntryZ e, StringBuilder sb) {
		LOGGER.info(() -> {
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
		});
	}
	@Override
	public String toString() {
		return "CacheDir [currentFile=" + currentFile + ", cacheDir=" + root + "]";
	}
	public EntryZ newEntry(String title, RootEntryZ root) {
		return new EntryZ(root, ++maxId, title, true);
	}
	public EntryZ newEntry(EntryZ d, RootEntryZ root) {
		Path src = d.getRoot().getCacheDir().contentPath(d);
		EntryZ nnew = new EntryZ(root, ++maxId, d.getTitle(), true);
		nnew.setLastModified(d.getLastModified());

		move(src, contentPath(nnew));
		return nnew;
	}

	private class Temp {
		final int parent_id, order;
		final EntryZ entry;
		public Temp(int parent_id, int order, EntryZ e) {
			this.entry = e;
			this.parent_id = parent_id;
			this.order = order;
		}
	}
	private void zip(Path target) throws IOException {
		Path temp = Files.createTempFile(target.getFileName().toString(), null);

		synchronized (wbuffer) {
			try(OutputStream os = Files.newOutputStream(temp);
					ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {

				Path index = index();
				if(Files.notExists(index))
					return;

				byte[] buffer = wbuffer.get();
				write("index", buffer, index, zos);

				Path content = contentDir;
				if(Files.notExists(content)) return;

				String[] contents = content.toFile().list();
				if(contents == null || contents.length == 0) return;
				for (String f : contents)
					write("content/"+f, buffer, content.resolve(f), zos);
			}
		}
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		LOGGER.fine("MOVED: "+temp+ "  "+target);
		this.currentFile = target;
		saveLastModified();
	}

	private static void write(String name, byte[] buffer, Path file, ZipOutputStream zos) throws IOException {
		ZipEntry e = new ZipEntry(name);
		zos.putNextEntry(e);

		try(InputStream is = Files.newInputStream(file)) {
			int n = 0;
			while((n = is.read(buffer)) > 0)
				zos.write(buffer, 0, n);
			zos.closeEntry();
		}
	}

	private void prepareCache() throws FileNotFoundException, IOException {
		Path lm = lastModified();

		if(anyMatch(MyUtilsCheck::notExists, currentFile, lm) || currentFile.toFile().lastModified() != LongSerializer.read(lm)) 
			_prepareCache();	
		 else  
			LOGGER.info(() -> "CACHE LOADED: "+root);
	}
	private void _prepareCache() throws FileNotFoundException, IOException {
		if(Files.exists(root)) {
			FilesUtilsIO.deleteDir(root);
			LOGGER.info(() -> "DELETE cacheDir: "+root);
		}

		Files.createDirectories(contentDir);
		if(notExists(currentFile)) return;

		synchronized (wbuffer) {
			try(InputStream is = Files.newInputStream(currentFile);
					ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8) ) {

				byte[] buffer = wbuffer.get();
				ZipEntry z = null;

				while((z = zis.getNextEntry()) != null) {
					try(OutputStream out = Files.newOutputStream(root.resolve(z.getName()))) {
						int n = 0;
						while((n = zis.read(buffer)) > 0)
							out.write(buffer, 0, n);
					}
				}
			}
			LOGGER.info(() -> "CACHE CREATED: "+this);
			saveLastModified();
			pathToCacheDir.put(this);
		}
	}
	private void saveLastModified() throws IOException {
		if(notExists(currentFile)) return;
		LongSerializer.write(currentFile.toFile().lastModified(), lastModified());
		StringWriter2.setText(this.root.resolve("file"), currentFile.toString());
	}
	@Override
	public void close() throws Exception {
		Util.hide(() -> FilesUtilsIO.deleteDir(removedDir));
	}
	public void restore(EntryZ e, Path removePath) throws IOException {
		Files.list(removePath)
		.forEach(p -> move(p, contentDir.resolve(p.getFileName())));
	}

	private static int counter = 0; 

	public Path remove(EntryZ e) throws IOException {
		Path dir = removedDir.resolve((counter++)+"-"+e.id);
		Files.createDirectories(dir);
		e.walk(d -> move(contentPath((EntryZ)d), dir.resolve(String.valueOf(d.id))));
		move(contentPath(e), dir.resolve(String.valueOf(e.id)));
		return dir;
	}
	private void move(Path src, Path target) {
		if(Files.notExists(src)) return;
		if(Util.hide(() -> Files.move(src, target, StandardCopyOption.REPLACE_EXISTING)))
			LOGGER.fine(() -> "MOVED: "+src +" -> "+target);
	}
}

