package sam.noter.dao.zip;


import static java.nio.charset.CodingErrorAction.*;
import static sam.io.IOUtils.pipe;
import static sam.myutils.Checker.anyMatch;
import static sam.myutils.Checker.notExists;
import static sam.noter.Utils.*;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import sam.collection.IndexedMap;
import sam.collection.IntSet;
import sam.io.BufferSupplier;
import sam.io.IOConstants;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.infile.DataMeta;
import sam.io.infile.InFile;
import sam.io.infile.TextInFile;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.ObjectReader;
import sam.io.serilizers.ObjectWriter;
import sam.myutils.Checker;
import sam.myutils.ThrowException;
import sam.nopkg.AutoCloseableWrapper;
import sam.nopkg.Junk;
import sam.nopkg.SavedAsStringResource;
import sam.nopkg.SavedResource;
import sam.noter.dao.RootEntry;
import sam.reference.ReferenceUtils;
import sam.reference.WeakAndLazy;
import sam.reference.WeakPool;
import sam.string.StringUtils.StringSplitIterator;

class CacheDir implements AutoCloseable {

	private static final Object LOCK = new Object();

	private static final byte[] bytes = new byte[IOConstants.defaultBufferSize()];
	private static final ByteBuffer buffer = ByteBuffer.wrap(bytes);
	private static final CharBuffer chars = CharBuffer.allocate(100);
	private static final WeakAndLazy<StringBuilder> wsink = new WeakAndLazy<>(StringBuilder::new);

	private static final Charset CHARSET = StandardCharsets.UTF_8; 
	private static final CharsetDecoder decoder = CHARSET.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);
	private static final CharsetEncoder encoder = CHARSET.newEncoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);

	private static final int MAX_ID = 0;
	private static final int LAST_MODIFIED = 1;
	private static final int SELECTED_ITEM = 2;
	private static final int MOD = 3;

	private static final int SIZE = 4;

	private static final int TITLE = 0x111;
	private static final int CONTENT = 0x222;

	private static final String INDEX = "index";
	private static final String CONTENT_PREFIX = "content/";
	private static final String CONTENT_PREFIX_2 = "content\\";

	private static final Logger logger = LogManager.getLogger(CacheDir.class);

	private IndexedMap<EntryCache> entries;
	private final SavedResource<Path> savedSourceLoc;
	
	private TextInFile contentTextfile;
	private TextInFile titleTextfile;
	
	public Path source;
	public final Path cacheDir;
	private final long[] meta;
	private int mod;
	private final BitSet modified = new BitSet();
	private final BitSet newEntries = new BitSet();

	public static class EntryCache {
		public final int id;
		private DataMeta title;
		private DataMeta content;

		public EntryCache(int id) {
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
			EntryCache other = (EntryCache) obj;
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

	public Path getSourceFile() {
		return source;
	}
	@Deprecated //moving code to proper places
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
		//moved to _prepareCache();
		onFound.accept(temp);
		mod++;
		saveCache(root0);
	}

	private void write(int id, String content, int type) throws IOException {
		
		if(Checker.isEmpty(content)) 
			metaSet(id, new DataMeta(0, 0), type);
		else {
			TextInFile file = file(type);

			synchronized (LOCK) {
				buffer.clear();
				DataMeta d = file.write(content, encoder, buffer, REPORT, REPORT);
				metaSet(id, d, type);
			}	
		}
	}

	private EntryCache metaSet(int id, DataMeta meta, int type) {
		EntryCache cache = entries.get(id);
		if(cache == null)
			entries.put(cache = new EntryCache(id));

		switch (type) {
			case TITLE:   
				cache.title = meta;
				break;
			case CONTENT: 
				cache.content = meta;
				break;
			default:
				throw new IllegalArgumentException("unknown type: "+type);
		}
		modified.set(id);
		mod++;
		return cache;
	}
	private DataMeta meta(EntryCache ec, int type) {
		if(ec == null)
			return null;

		switch (type) {
			case TITLE:   return ec.title;
			case CONTENT: return ec.content;
			default:
				throw new IllegalArgumentException("unknown type: "+type);
		}
	} 
	private TextInFile file(int type) {
		switch (type) {
			case TITLE:   return titleTextfile;
			case CONTENT: return contentTextfile;
			default:
				throw new IllegalArgumentException("unknown type: "+type);
		}
	} 

	private String read(int id, int type) throws IOException {
		DataMeta dm = meta(entries.get(id), type);

		if(dm == null)
			return null;

		if(dm.size == 0)
			return "";

		TextInFile file = file(type);

		synchronized (LOCK) {
			buffer.clear();
			chars.clear();
			StringBuilder sink = wsink.get();
			sink.setLength(0);

			file.readText(dm, buffer, chars, decoder, sink, REPORT, REPORT);
			return sink.length() == 0 ? "" : sink.toString();
		}
	}

	public String readContent(EntryZ e) throws IOException {
		return read(e.id, CONTENT);
	}
	public void writeContent(EntryZ e) throws IOException {
		write(e.id, e.getContent(), CONTENT);
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
					if(e.isContentModified()) 
						writeContent(e);

					logModification(e, sb);
				}
			}
			walk(e, sink, sb, lines);
		}
	}

	private void logModification(EntryZ e, StringBuilder sb) {
		logger.debug(() -> {
			if(newEntries.get(e.id))
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
		newEntries.set(e.id);
		return e;

	}
	private int nextId() {
		return (int)(meta[MAX_ID] = meta[MAX_ID] + 1);
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
		synchronized (LOCK) {
			Path temp = Files.createTempFile(target.getFileName().toString(), null);

			try(OutputStream os = Files.newOutputStream(temp);
					ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8);) {

				Path index = index();
				if(Files.notExists(index))
					return temp;

				contentTextfile.read(meta, buffer, bufferConsumer);
				byte[] buffer = wb.get();
				write("index", buffer, index, zos);

				if(entries == null || entries.isEmpty())
					return temp;

				String[] contents = content.toFile().list();
				if(contents == null || contents.length == 0) return temp;
				for (String f : contents) //FIXME
					write(CONTENT_PREFIX+f, buffer, content.resolve(f), zos);
			}
			return temp;

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

		synchronized(LOCK) {
			try(InputStream _is = Files.newInputStream(source);
					BufferedInputStream bis = new BufferedInputStream(_is);
					ZipInputStream zis = new ZipInputStream(bis, StandardCharsets.UTF_8);
					) {

				Files.createDirectories(cacheDir);
				Path p = resolve("content");
				Files.deleteIfExists(p);
				contentTextfile = new TextInFile(p, true);
				buffer.clear();
				Map<Integer, DataMeta> titleMap = null;

				ZipEntry z = null;
				ArrayList<EntryCache> positions = new ArrayList<>();

				while((z = zis.getNextEntry()) != null) {
					String name = z.getName();
					
					if(name.equals(INDEX)) {
						titleMap = parseEntries(zis);
					} else {
						int id = getId(name);
						
						if(id != NO_ID) {
							DataMeta d = contentTextfile.write(new BufferSupplier() {
								int n = 0;

								@Override
								public ByteBuffer next() throws IOException {
									n = zis.read(bytes);
									if(n < 0)
										return null;

									buffer.clear();
									buffer.limit(n);

									return buffer;
								}

								@Override
								public boolean isEndOfInput() throws IOException {
									return n < 0;
								}
							});

							EntryCache c = new EntryCache(id);
							c.content = d;
							positions.add(c);
						} else {
							logger.debug("unknown file in zip: {}", name);
						}
						
					}
				}

				if(Checker.isNotEmpty(titleMap))  {
					Map<Integer, DataMeta> map = titleMap;
					positions.forEach(e -> e.title = map.get(e.id));
				}
				
				this.entries = new IndexedMap<>(positions.toArray(new EntryCache[0]), d -> d.id);
			}
			logger.info("CACHE CREATED: {}", this);
		}
	}

	private Map<Integer, DataMeta> parseEntries(InputStream zis) throws UnsupportedEncodingException, IOException {
		HashMap<Integer, Temp> map = new HashMap<>();
		RootEntryZ root0 = Junk.notYetImplemented();// FIXME

		try(InputStreamReader isr = new InputStreamReader(zis, "utf-8");
				BufferedReader reader = new BufferedReader(isr);
				) {
			if(titleTextfile == null)
				throw new IllegalArgumentException();

			Path p = resolve("titles");
			Files.deleteIfExists(p);
			titleTextfile = new TextInFile(p, true);
			Map<Integer, DataMeta> dmMap = new HashMap<>();
			Map<Integer, List<Temp>> grouped = new HashMap<>();
			
			String s = null;
			while((s = reader.readLine()) != null) {
				Iterator<String> iter = new StringSplitIterator(s, ' ', 5);
				int id = Integer.parseInt(iter.next());
				int parent_id = Integer.parseInt(iter.next());
				int order = Integer.parseInt(iter.next());
				long lastModified = Long.parseLong(iter.next());
				String title = iter.next();

				Temp  t = new Temp(parent_id, order, new EntryZ(root0, id, lastModified, title));
				meta[MAX_ID] = Math.max(meta[MAX_ID], id);
				map.put(id, t);
				
				buffer.clear();
				dmMap.put(id, titleTextfile.write(title, encoder, buffer, REPORT, REPORT));
			}

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
			
			return dmMap;
		}
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
		if(contentTextfile != null)
			contentTextfile.close();
		contentTextfile = null;
	}
	public int getSelectedItem() {
		return (int) meta[SELECTED_ITEM];
	}
}

