package sam.noter.dao.zip;

import static sam.myutils.Checker.anyMatch;
import static sam.myutils.Checker.isEmpty;
import static sam.myutils.Checker.notExists;

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
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;

import sam.collection.IntSet;
import sam.io.IOConstants;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.IntSerializer;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;
import sam.noter.dao.zip.RootEntryZFactory.PathToCacheDir;
import sam.reference.ReferenceUtils;
import sam.reference.WeakAndLazy;
import sam.string.StringUtils.StringSplitIterator;

class CacheDir {
	private static final int MAX_ID = 0;
	private static final int LAST_MODIFIED = 1;
	private static final int SELECTED_ITEM = 2;
	
	private static final Logger logger = LogManager.getLogger(CacheDir.class);
	private static final WeakAndLazy<byte[]> wbuffer = new WeakAndLazy<>(() -> new byte[IOConstants.defaultBufferSize()]);
	private final IntSet newEntries = new IntSet();

	private final Path source;
	public final Path cacheDir;
	private int maxId;
	private WeakReference<Map<Integer, String>> lines;

	public CacheDir(Path source, Path cacheDir) throws IOException {
		this.source = source;
		this.cacheDir = cacheDir;
		prepareCache();

	}
	public Path getSourceFile() {
		return source;
	}
	public void loadEntries(RootEntryZ root) throws IOException, ClassNotFoundException {
		Path index = index2();
		if(Files.exists(index)) {
			Path p = index;
			logger.debug(() -> "index loaded: "+p);
			maxId = new IntSerializer().read(maxId()); 
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
	
	public String getContent(EntryZ e) throws IOException {
		Path p = contentPath(e);
		if(Files.notExists(p))
			return null;
		String  s = StringReader2.getText(p);
		logger.debug(() -> "CONTENT LOADED: "+e);
		return s;
	}
	public void save(RootEntryZ root, Path file) throws IOException {
		if(!root.isModified()) return;

		StringBuilder sv2 = new StringBuilder(100);
		ArrayList<String> list = new ArrayList<>(); 

		Map<Integer, String> lines = ReferenceUtils.get(this.lines);
		if(lines == null) {
			Path index = index();
			if(Files.notExists(index))
				lines = new HashMap<>();
			else {
				lines = Files.lines(index).collect(Collectors.toMap(s -> Integer.parseInt(s.substring(0, s.indexOf(' '))), s -> s));
				this.lines = new WeakReference<Map<Integer,String>>(lines);
				logger.debug(() -> "loaded for lines: "+index);
			}
		}

		walk(root, list, sv2, lines);
		Files.write(index(), list, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		saveRoot(root);
		zip(file);
		this.currentFile = file;
		pathToCacheDir.put(this);
		newEntries.clear();
	}

	private void saveRoot(RootEntryZ root) throws IOException {
		new IntSerializer().write(maxId, maxId());
		ObjectWriter.write(index2(), root, RootEntryZ::write);
		logger.debug(() -> "CREATED: "+maxId()+" (maxId: "+maxId+")");
		logger.debug(() -> "CREATED: "+index2());
	}

	private void walk(EntryZ entry, List<String> sink, StringBuilder sb, Map<Integer, String> lines) throws IOException {
		@SuppressWarnings("rawtypes")
		List list = entry.getChildren();
		if(list.isEmpty()) return;

		int parentId = entry.getId();

		int order = 0;
		boolean cM = entry.isChildrenModified();

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
								logger.debug(() -> "DELETED: "+p);
						} else
							StringWriter2.setText(p, s);
					}
					logModification(e, sb);
				}
			}
			walk(e, sink, sb, lines);
		}
	}
	private void logModification(EntryZ e, StringBuilder sb) {
		logger.debug(() -> {
			if(newEntries.contains(e.id))
				return "NEW "+e;
			
			sb.setLength(0);

			sb.append("UPDATED ").append(e).append(" [");
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
		return "CacheDir [currentFile=" + currentFile + ", cacheDir=" + cacheDir + "]";
	}
	public EntryZ newEntry(String title, RootEntryZ root) {
		EntryZ e = new EntryZ(root, ++maxId, title, true);
		newEntries.add(e.id);
		return e;
	
	}
	public EntryZ newEntry(EntryZ d, RootEntryZ root) {
		Path src = d.getRoot().getCacheDir().contentPath(d);
		EntryZ nnew = new EntryZ(root, ++maxId, d.getTitle(), true);
		nnew.setLastModified(d.getLastModified());
		newEntries.add(nnew.id);

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
		Path temp = _zip(target);
		
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		logger.debug("MOVED: "+temp+ "  "+target);
		this.currentFile = target;
		saveLastModified();
	} 
	
	private Path _zip(Path target) throws IOException {
		Path temp = Files.createTempFile(target.getFileName().toString(), null);

		synchronized (wbuffer) {
			try(OutputStream os = Files.newOutputStream(temp);
					ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)) {

				Path index = index();
				if(Files.notExists(index))
					return temp;

				byte[] buffer = wbuffer.get();
				write("index", buffer, index, zos);

				Path content = contentDir;
				if(Files.notExists(content)) return temp;

				String[] contents = content.toFile().list();
				if(contents == null || contents.length == 0) return temp;
				for (String f : contents)
					write("content/"+f, buffer, content.resolve(f), zos);
			}
		}
		return temp;
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

		if(anyMatch(Checker::notExists, currentFile, lm) || currentFile.toFile().lastModified() != new LongSerializer().read(lm)) 
			_prepareCache();	
		else  
			logger.info(() -> "CACHE LOADED: "+root);
	}
	private void _prepareCache() throws FileNotFoundException, IOException {
		if(Files.exists(cacheDir)) {
			FilesUtilsIO.deleteDir(cacheDir);
			logger.info(() -> "DELETE cacheDir: "+root);
		}

		Files.createDirectories(contentDir);
		if(notExists(currentFile)) return;

		synchronized (wbuffer) {
			try(InputStream is = Files.newInputStream(currentFile);
					ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8) ) {

				byte[] buffer = wbuffer.get();
				ZipEntry z = null;

				while((z = zis.getNextEntry()) != null) {
					try(OutputStream out = Files.newOutputStream(cacheDir.resolve(z.getName()))) {
						int n = 0;
						while((n = zis.read(buffer)) > 0)
							out.write(buffer, 0, n);
					}
				}
			}
			logger.info(() -> "CACHE CREATED: "+this);
			saveLastModified();
			pathToCacheDir.put(this);
		}
	}
	private void saveLastModified() throws IOException {
		if(notExists(currentFile)) return;
		new LongSerializer().write(currentFile.toFile().lastModified(), lastModified());
		StringWriter2.setText(this.cacheDir.resolve("file"), currentFile.toString());
	}
	public void close(RootEntryZ ez) {
		Entry selectedItem = ez.getSelectedItem();
		
		Util.hide(() -> {
			Path p = resolve("selecteditem");
			Files.deleteIfExists(p);
			if(selectedItem != null)
				new IntSerializer().write(selectedItem.id, p);

			FilesUtilsIO.deleteDir(removedDir);
		});

	}
	public int getSelectedItem() {
		Path p = resolve("selecteditem");
		if(Files.notExists(p)) return -1;
		return Util.get(() -> new IntSerializer().read(p), -1);
	}
	private static int counter = 0;
	private HashMap<EntryZ, Path> removedMap;

	public void remove(EntryZ e) throws IOException {
		Objects.requireNonNull(e);
		Path dir = removedDir.resolve((counter++)+"-"+e.id);
		Files.createDirectories(dir);
		e.walk(d -> move(contentPath((EntryZ)d), dir.resolve(String.valueOf(d.id))));
		move(contentPath(e), dir.resolve(String.valueOf(e.id)));
		if(removedMap == null)
			removedMap = new HashMap<>();
		removedMap.put(e, dir);
	}
	public void restore(EntryZ e) throws IOException {
		if(isEmpty(removedMap))
			return;
		Path dir  = removedMap.remove(e);
		if(notExists(dir)) return;
		
		Files.list(dir)
		.forEach(p -> move(p, contentDir.resolve(p.getFileName())));
	}
	private void move(Path src, Path target) {
		if(Files.notExists(src)) return;
		if(Util.hide(() -> Files.move(src, target, StandardCopyOption.REPLACE_EXISTING)))
			logger.debug(() -> "MOVED: "+src +" -> "+target);
	}
}

