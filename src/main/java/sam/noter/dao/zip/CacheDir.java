package sam.noter.dao.zip;

import static java.nio.charset.CodingErrorAction.REPORT;
import static sam.myutils.Checker.anyMatch;
import static sam.myutils.Checker.isEmpty;
import static sam.myutils.Checker.notExists;
import static sam.io.fileutils.FilesUtilsIO.*;


import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.collection.IndexedMap;
import sam.collection.IntSet;
import sam.io.IOConstants;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.infile.DataMeta;
import sam.io.infile.InFile;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.io.serilizers.StringReader2.ReaderConfig;
import sam.myutils.Checker;
import sam.myutils.ThrowException;
import sam.nopkg.AutoCloseableWrapper;
import sam.nopkg.SavedAsStringResource;
import sam.nopkg.SavedResource;
import static sam.noter.Utils.*;

import sam.noter.Utils;
import sam.noter.dao.RootEntry;
import sam.reference.ReferenceUtils;
import sam.reference.WeakPool;
import sam.string.StringUtils.StringSplitIterator;
import static sam.io.IOUtils.*;

class CacheDir implements AutoCloseable {
	private static final int MAX_ID = 0;
	private static final int LAST_MODIFIED = 1;
	private static final int SELECTED_ITEM = 2;
	private static final int MOD = 3;

	private static final int SIZE = 4;

	private static final String INDEX = "index";
	private static final String CONTENT_PREFIX = "content/";
	private static final String CONTENT_PREFIX_2 = "content\\";

	private static final Logger logger = LogManager.getLogger(CacheDir.class);

	private final IntSet newEntries = new IntSet();
	private IndexedMap<Position> positions;

	private final SavedResource<Path> savedSourceLoc;
	private InFile cached;
	private long cached_size = -1;
	public Path source;
	public final Path cacheDir;
	private final long[] meta;
	private int mod;
	private WeakReference<Map<Integer, String>> lines;

	public static class Position extends DataMeta {
		public final int id;

		protected Position(int id) {
			super(-1, -1);
			this.id = id;
		}

		public Position(int id, long position, long size) {
			super(position, size);

			if(position > Integer.MAX_VALUE)
				ThrowException.illegalArgumentException("position("+position+") > Integer.MAX_VALUE");
			if(size > Integer.MAX_VALUE)
				ThrowException.illegalArgumentException("size("+size+") > Integer.MAX_VALUE");

			this.id = id;
		}

		@Override
		public int hashCode() {
			return id;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Position other = (Position) obj;
			return id == other.id;
		}



	}

	public CacheDir(Path source, Path cacheDir) throws IOException {
		this.source = source;
		this.cacheDir = cacheDir;

		Path p = meta(); 
		long[] meta = Files.notExists(p) ? null : new LongSerializer().readArray(p);
		savedSourceLoc = new SavedAsStringResource<Path>(cacheDir.resolve("file"), Paths::get);
		Path t = savedSourceLoc.get();
		if(t != null && !t.equals(source))
			throw new IOException(String.format("source mismatch, expected=\"%s\", supplied: \"%s\"", savedSourceLoc.get(), source));

		init(meta);

		if(meta == null) {
			meta = new long[SIZE];
			Arrays.fill(meta, -1);
		}
		this.meta = meta;
	}

	private Path resolve(String s) { return cacheDir.resolve(s); }

	private Path meta() { return resolve("meta"); }
	private Path content() { return resolve("content"); }
	private Path index2() { return resolve("index2"); }
	private Path index() { return resolve(INDEX); }

	public Path getSourceFile() {
		return source;
	}
	public void loadEntries(@SuppressWarnings("rawtypes") Consumer<List> onFound, RootEntryZ root0) throws IOException, ClassNotFoundException {
		Path index = index2();
		if(Files.exists(index)) {
			Path p = index;
			onFound.accept(ObjectReader.<EntryZ>read(index, dis -> EntryZ.read(dis, root0)).getChildren());
			logger.debug("index loaded: {}", p);
			return;
		}
		index = index();

		if(Files.notExists(index)) {
			logger.warn("not found: {}", index);
			return; 
		}

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

					Temp  t = new Temp(parent_id, order, new EntryZ(root0, id, lastModified, title));
					meta[MAX_ID] = Math.max(meta[MAX_ID], id);
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

		onFound.accept(temp);
		this.lines = new WeakReference<Map<Integer,String>>(lines);
		mod++;
		saveCache(root0);
	}

	private Position write(int id, String content) throws IOException {
		if(Checker.isEmpty(content))
			return new Position(id);

		DataMeta d = encodeNWrite(content, cached);
		
		if(d == null)
			return new Position(id, -1, -1);
		else
			return new Position(id, d.position, d.size);
	}

	private AutoCloseableWrapper<ByteBuffer> read(Position pos) throws IOException {
		if(pos == null || pos.size <= 0 || pos.position < 0)
			return null;

		ByteBuffer buffer = wbuff.poll();
		buffer.clear();
		if(buffer.capacity() < pos.size) {
			wbuff.add(buffer);
			buffer = ByteBuffer.allocate(pos.size);
		}

		buffer.clear();
		cached.read(buffer, pos);
		
		if(buffer.hasRemaining()) {
			int n = buffer.remaining();
			buffer.clear();
			wbuff.add(buffer);
			throw new IOException("incomplete read, expected:"+pos.size+", actual: "+(pos.size - n));
		}

		buffer.flip();
		ByteBuffer b = buffer;
		return new AutoCloseableWrapper<>(() -> b, bb -> {
			bb.clear();
			wbuff.add(bb);
		});
	}

	private String parse(AutoCloseableWrapper<ByteBuffer> acw) throws IOException {
		if(acw == null)
			return null;

		try(AutoCloseableWrapper<ByteBuffer> acw2 = acw) {
			return decode(acw.get());
		}
	}
	public String getContent(EntryZ e) throws IOException {
		Position pos = position(e);
		if(pos == null)
			return null;
		String s = parse(read(pos));
		logger.debug("CONTENT LOADED: {}",  e);
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
		mod++;
		zip(file);
		saveCache(root);
		savedSourceLoc.set(file);
		savedSourceLoc.close();
		this.source = file;
		newEntries.clear();
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
						positions.put(write(e.id, e.getContent()));
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
		return "CacheDir [currentFile=\"" + source + "\", cacheDir=\"" + subpath(cacheDir) + "\"]";
	}
	public EntryZ newEntry(String title, RootEntryZ root) {
		EntryZ e = new EntryZ(root, nextId(), title, true);
		newEntries.add(e.id);
		return e;

	}
	private int nextId() {
		return (int)(meta[MAX_ID] = meta[MAX_ID] + 1);
	}
	private Position position(EntryZ d) {
		return positions.get(d.id);
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
		this.source = target;
		setLastModified();
	} 
	private void setLastModified() throws IOException {
		meta[LAST_MODIFIED] = source.toFile().lastModified();
	}
	private Path _zip(Path target) throws IOException {
		Path temp = Files.createTempFile(target.getFileName().toString(), null);

		try(OutputStream os = Files.newOutputStream(temp);
				ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8);
				AutoCloseableWrapper<byte[]> wb = wwbytes.autoCloseableWrapper()) {

			Path index = index();
			if(Files.notExists(index))
				return temp;

			byte[] buffer = wb.get();
			write("index", buffer, index, zos);

			if(cached_size <= 0 || positions == null || positions.isEmpty())
				return temp;

			String[] contents = content.toFile().list();
			if(contents == null || contents.length == 0) return temp;
			for (String f : contents) //FIXME
				write(CONTENT_PREFIX+f, buffer, content.resolve(f), zos);
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

	private void init(long[] meta) throws FileNotFoundException, IOException {
		if(meta == null || anyMatch(Checker::notExists, source, content(), index2()) || source.toFile().lastModified() != meta[LAST_MODIFIED]) 
			_prepareCache();	
		else  
			logger.debug("CACHE LOADED: {}",() -> subpath(cacheDir));
	}

	// TODO

	private void _prepareCache() throws FileNotFoundException, IOException {
		if(Files.exists(cacheDir)) {
			FilesUtilsIO.deleteDir(cacheDir);
			logger.debug("DELETE cacheDir: {}", () -> subpath(cacheDir));
		}

		if(notExists(source)) 
			return;

		Files.createDirectories(cacheDir);

		cached = new InFile(content(), true);

		try(InputStream is = Files.newInputStream(source);
				ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8); 
				AutoCloseableWrapper<byte[]> cb = wwbytes.autoCloseableWrapper();) {

			byte[] bytes = cb.get();
			ZipEntry z = null;
			ArrayList<Position> positions = new ArrayList<>();

			while((z = zis.getNextEntry()) != null) {
				String name = z.getName();

				int id = getId(name);
				if(id != NO_ID) {
					long position = cached.position();
					long size = cached.write(zis, bytes);
					positions.add(new Position(id, position, size));
				} else {
					pipe(zis, cacheDir.resolve(name), bytes);
				}
			}
			this.cached_size = cached.position();
			this.positions = new IndexedMap<>(positions.toArray(new Position[0]), p -> p.id);
		}
		logger.info("CACHE CREATED: {}", this);

	}

	private static final int NO_ID = -10;

	private int getId(String name) {
		try {
			if(name.startsWith(CONTENT_PREFIX))
				return Integer.parseInt(name.substring(CONTENT_PREFIX.length()));
			if(name.startsWith(CONTENT_PREFIX_2))
				return Integer.parseInt(name.substring(CONTENT_PREFIX_2.length()));
		} catch (NumberFormatException e) {
			logger.catching(e);
		}
		return NO_ID;
	}

	private String subpath(Path p) {
		return subpathWithPrefix(p);
	}
	private void saveCache(RootEntryZ root) throws IOException {
		if(mod == 0)
			return;

		int n = root.getSelectedItem().id;
		if(n != this.meta[SELECTED_ITEM]) {
			this.meta[SELECTED_ITEM] = n;
			mod++;
		}

		if(mod == 0)
			logger.debug("saving skipped: mod == 0, {}", this);

		this.meta[SELECTED_ITEM] = n;
		new LongSerializer().write(meta, meta());
		ObjectWriter.write(index2(), root, RootEntryZ::write);
		logger.debug("saveCache {}\n  meta: ", () -> this, () -> Arrays.toString(meta));
		mod = 0;
	}

	@Override
	public void close() throws IOException {
		if(cached != null)
			cached.close();
		cached = null;
	}
	public int getSelectedItem() {
		return (int) meta[SELECTED_ITEM];
	}
}

