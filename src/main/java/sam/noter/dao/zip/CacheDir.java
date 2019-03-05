package sam.noter.dao.zip;


import static java.nio.charset.CodingErrorAction.REPORT;
import static sam.myutils.Checker.anyMatch;
import static sam.myutils.Checker.notExists;
import static sam.noter.Utils.subpathWithPrefix;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.collection.IndexedMap;
import sam.functions.IOExceptionBiConsumer;
import sam.functions.IOExceptionConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.io.serilizers.LongSerializer;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.nopkg.SavedAsStringResource;
import sam.nopkg.SavedResource;
import sam.nopkg.SimpleSavedResource;
import sam.nopkg.StringResources;
import sam.noter.dao.RootEntry;
import sam.string.StringSplitIterator;

class CacheDir implements AutoCloseable {


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
	private final SavedAsStringResource<Path> savedSourceLoc;
	private final SavedAsStringResource<long[]> _cacheMeta;
	private final long[] cacheMeta;

	private TextInFile contentTextfile;
	private TextInFile titleTextfile;

	public Path source;
	public final Path cacheDir;
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
		this._cacheMeta = _metaStringResources();
		savedSourceLoc = new SavedAsStringResource<Path>(resolve("source-file-location"), Paths::get);

		Path t = savedSourceLoc.get();
		if(t != null && !t.equals(source))
			throw new IOException(String.format("source mismatch, expected=\"%s\", supplied: \"%s\"", savedSourceLoc.get(), source));

		long[] meta = this._cacheMeta.get();

		init(meta);

		if(meta == null) {
			meta = new long[SIZE];
			Arrays.fill(meta, -1);

			this._cacheMeta.set(meta);
		}
		this.cacheMeta = this._cacheMeta.get();
	}

	private SavedResource<long[]> _metaStringResources() {
		Function<Path, long[]> reader = p -> {
			try {
				logger.debug("read: {}", p);
				return new LongSerializer().readArray(p);
			} catch (IOException e) {
				logger.warn("failed to read: {}",p, e);
			}
			return null;
		};

		IOExceptionBiConsumer<Path, long[]> writer = (path, array) -> {
			new LongSerializer().write(array, path);
			logger.debug("write: {}", path);
		};

		return new SimpleSavedResource<>(resolve("meta"), reader, writer, Arrays::equals);
	}

	private Path resolve(String s) { return cacheDir.resolve(s); }

	public Path getSourceFile() {
		return source;
	}
	@Deprecated //moving code to proper places
	public void loadEntries(@SuppressWarnings("rawtypes") Consumer<List> onFound, RootEntryZ root0) throws IOException, ClassNotFoundException {
		/** FIXME
		 * 		Path index = index2();
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
		// loading entries moved to _prepareCache();
		onFound.accept(temp);
		mod++;
		saveCache(root0);
		 */

	}

	private void write(int id, String content, int type) throws IOException {
		if(Checker.isEmpty(content)) 
			dataMetaSet(id, new DataMeta(0, 0), type);
		else {
			TextInFile file = file(type);
			try(StringResources r = StringResources.get()) {
				DataMeta d = file.write(content, r.encoder, r.buffer, REPORT, REPORT);
				dataMetaSet(id, d, type);
			}
		}
	}

	private EntryCache dataMetaSet(int id, DataMeta meta, int type) {
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

		try(StringResources r = StringResources.get()) {
			StringBuilder sink = r.wsink.get();

			file.readText(dm, r.buffer, r.chars, r.decoder, sink, REPORT, REPORT);
			return sink.length() == 0 ? "" : sink.toString();
		}

	}

	public String readContent(EntryZ e) throws IOException {
		return read(e.id, CONTENT);
	}
	public void writeContent(EntryZ e) throws IOException {
		write(e.id, e.getContent(), CONTENT);
		e.setContentModified(false);
	}

	public void save(RootEntryZ root, Path file) throws IOException {
		if(!root.isModified()) return;

		try(StringResources r = StringResources.get()) {
			mod++;
			zip(file, r, root);
			saveCache(root);
			savedSourceLoc.set(file);
			savedSourceLoc.close();
			this.source = file;
		}
	}

	private void writeIndex(final OutputStream out, final EntryZ entry, final StringResources r, final List<EntryCache> entryCaches, final StringBuilder sink, final int maxSinkSize) throws IOException {
		@SuppressWarnings("rawtypes")
		List list = entry.getChildren();
		if(list.isEmpty()) 
			return;

		int parentId = entry.getId();
		int order = 0;

		for (Object ti : list) {
			EntryZ e = (EntryZ) ti;

			sink.append(e.id).append(' ')
			.append(parentId).append(' ')
			.append(order++).append(' ')
			.append(e.getLastModified()).append(' ')
			.append(e.getTitle());

			if(e.isModified()) {
				if(e.isContentModified()) 
					writeContent(e);

				if(logger.isDebugEnabled())
					logModification(e);
			}

			entryCaches.add(entries.get(e.id)); //FIXME get from entryz
			writeIndex(out, e, r, entryCaches, sink, maxSinkSize);

			if(sink.length() >= maxSinkSize) {
				logger.debug(() -> "writing text.length: "+ sink.length());
				write(sink, r, out);
				sink.setLength(0);
			}
		}
	}

	private void write(CharSequence data, StringResources r, OutputStream out) throws IOException {
		if(data.length() == 0)
			return;

		IOUtils.ensureCleared(r.buffer);
		StringIOUtils.write(b -> write(b, r, out), data, r.encoder, r.buffer, REPORT, REPORT);
		logger.debug(() -> "WRITTEN text.length: "+ data.length());
	}

	private void write(ByteBuffer b, StringResources r, OutputStream out) throws IOException {
		if(b != r.buffer)
			throw new IllegalStateException();

		out.write(r.bytes, 0, b.limit());
		b.clear();
	}

	private void logModification(EntryZ e) {
		logger.debug(() -> {
			if(newEntries.get(e.id))
				return "NEW "+e;

			StringBuilder sb = new StringBuilder();

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
		return (int)(cacheMeta[MAX_ID] = cacheMeta[MAX_ID] + 1);
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

	private void zip(Path target, StringResources r, RootEntryZ root) throws IOException {
		Path temp = _zip(target, r, root);

		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		logger.debug("MOVED: "+temp+ "  "+target);
		this.source = target;
		setLastModified();
	} 
	private void setLastModified() throws IOException {
		cacheMeta[LAST_MODIFIED] = source.toFile().lastModified();
	}
	private Path _zip(Path target, StringResources r, RootEntryZ rootEntry) throws IOException {
		IOUtils.ensureCleared(r.buffer);

		StringBuilder sb = r.sb();
		if(sb.length() != 0)
			throw new IllegalArgumentException("sb.length("+sb.length()+") != 0");

		Path temp = Files.createTempFile(target.getFileName().toString(), null);

		try(OutputStream _os = Files.newOutputStream(temp);
				BufferedOutputStream bos = new BufferedOutputStream(_os);
				ZipOutputStream zos = new ZipOutputStream(bos, r.CHARSET);) {

			if(rootEntry.isEmpty())
				return temp;

			IOExceptionConsumer<String> putEntry = name -> {
				ZipEntry z = new ZipEntry(name);
				zos.putNextEntry(z);
			};

			putEntry.accept(INDEX);

			List<EntryCache> entries = new ArrayList<>();
			int maxSinkSize = (int) (r.bytes.length/r.encoder.averageBytesPerChar());
			logger.debug(() -> "maxSinkSize: "+maxSinkSize);

			for (Object e : rootEntry.getChildren()) 
				writeIndex(zos, (EntryZ)e, r, entries, sb, maxSinkSize);

			write(sb, r, zos);
			zos.closeEntry();

			if(Checker.isEmpty(entries))
				return temp;

			for (EntryCache e : entries) {
				final DataMeta d = e == null ? null : e.content;
				if(d == null || d.size == 0)
					continue;

				r.buffer.clear();
				putEntry.accept(CONTENT_PREFIX+e.id);
				contentTextfile.read(d, r.buffer, b -> write(b, r, zos));
				zos.closeEntry();
			}
		}
		return temp;
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
		try(InputStream _is = Files.newInputStream(source);
				BufferedInputStream bis = new BufferedInputStream(_is);
				ZipInputStream zis = new ZipInputStream(bis, StandardCharsets.UTF_8);
				StringResources r = StringResources.get(); ) {

			Files.createDirectories(cacheDir);
			Path p = resolve("content");
			Files.deleteIfExists(p);
			contentTextfile = new TextInFile(p, true);
			Map<Integer, DataMeta> titleMap = null;



			ZipEntry z = null;
			ArrayList<EntryCache> positions = new ArrayList<>();

			while((z = zis.getNextEntry()) != null) {
				String name = z.getName();

				if(name.equals(INDEX)) {
					titleMap = parseEntries(zis, r.buffer, r.encoder);
				} else {
					int id = getId(name);

					if(id != NO_ID) {
						DataMeta d = contentTextfile.write(new BufferSupplier() {
							int n = 0;

							@Override
							public ByteBuffer next() throws IOException {
								n = zis.read(r.bytes);
								if(n < 0)
									return null;

								r.buffer.clear();
								r.buffer.limit(n);

								return r.buffer;
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
		logger.debug("CACHE CREATED: {}", this);
	}

	private Map<Integer, DataMeta> parseEntries(InputStream zis, ByteBuffer buffer, CharsetEncoder encoder) throws UnsupportedEncodingException, IOException {
		IOUtils.ensureCleared(buffer);

		HashMap<Integer, Temp> map = new HashMap<>();
		RootEntryZ root0 = Junk.notYetImplemented();// FIXME

		try(InputStreamReader isr = new InputStreamReader(zis, encoder.charset());
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
				_cacheMeta.get()[MAX_ID] = Math.max(_cacheMeta.get()[MAX_ID], id);
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
		if(n != this.cacheMeta[SELECTED_ITEM]) {
			this.cacheMeta[SELECTED_ITEM] = n;
			mod++;
		}

		if(mod == 0)
			logger.debug("saving skipped: mod == 0, {}", this);

		this.cacheMeta[SELECTED_ITEM] = n;
		_cacheMeta.set(cacheMeta);
		_cacheMeta.close();

		//FIXME ObjectWriter.write(index2(), root, RootEntryZ::write);
		logger.debug("saveCache {}\n  meta: ", () -> this, () -> Arrays.toString(cacheMeta));
		mod = 0;
	}

	@Override
	public void close() throws IOException {
		if(contentTextfile != null)
			contentTextfile.close();
		contentTextfile = null;
	}
	public int getSelectedItem() {
		return (int) cacheMeta[SELECTED_ITEM];
	}
}

