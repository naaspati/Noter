package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.myutils.Checker;
import sam.nopkg.Resources;
import sam.reference.WeakAndLazy;

abstract class CachedRoot extends RootEntryZ {
	private static final Logger logger = LoggerFactory.getLogger(CachedRoot.class);
	private static final Object LOCK = new Object();

	private final Path indexPath, contentPath;
	private TextInFile content;
	private boolean closed = false;

	public CachedRoot(Path indexPath, Path contentPath) throws IOException {
		this.indexPath = indexPath;
		this.contentPath = contentPath;
		
		load();
	}

	private class ClosedRootEntryZException extends RuntimeException {
		private static final long serialVersionUID = 2809491276747637097L;
	}

	@Override
	protected void checkClosed() {
		if(closed)
			throw new ClosedRootEntryZException();
	}

	@Override
	public void close() throws IOException {
		super.close();

		closed = true;
		content.close();
		content = null;
	}

	protected abstract void saveMeta() throws IOException;
	protected abstract void updateLastModified();

	private void parseZip(ArrayList<TempEntry> entries) throws IOException {
		checkClosed();

		entries.clear();
		ZipExtractor z = new ZipExtractor();
		z.parseZip(getJbookPath(), content, entries);
		updateLastModified();
		saveMeta();
	}

	public void reload() throws IOException {
		checkClosed();
		deleteCache();
		load();
	}
	private void deleteCache() throws IOException {
		Files.deleteIfExists(indexPath);
		Files.deleteIfExists(contentPath);
	}

	private static final WeakAndLazy<ArrayList<TempEntry>> wbuffer = new WeakAndLazy<>(ArrayList::new);

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void load() throws IOException {
		checkClosed();

		synchronized (LOCK) {
			ArrayList<TempEntry> entries = wbuffer.get();
			entries.clear();
			boolean parsed = false;
			try {
				if(Files.notExists(indexPath) || Files.notExists(contentPath)) {
					deleteCache();

					this.content = new TextInFile(contentPath, true);
					logger.debug("parsing zip: {}", getJbookPath());
					parseZip(entries);
					parsed = true;
				} else {
					this.content = new TextInFile(contentPath, false);
					IndexHelper.readIndex(indexPath, entries);
					logger.debug("reading index: {}", indexPath);
					entries.removeIf(Objects::isNull);
				}

				Checker.assertTrue(this.id == -1);

				if(entries.isEmpty())
					init(new ArrayList<>(), new ArrayWrap<>(new EZ[0], EZ[]::new));
				else {
					int[] maxIdParentMaxId = {0,0};
					entries.forEach(e -> {
						if(e != null) {
							maxIdParentMaxId[0] = Math.max(e.id, maxIdParentMaxId[0]);
							maxIdParentMaxId[1] = Math.max(e.parent_id, maxIdParentMaxId[1]);
						}
					});

					EZ[] allEntries = new EZ[maxIdParentMaxId[0] + 1];
					int[] childCount = new int[entries.stream().mapToInt(t -> t.parent_id).max().getAsInt() + 2];
					entries.forEach(e -> childCount[e.parent_id + 1]++);
					List[] lists = new List[childCount.length];

					lists[0] = new ArrayList<>(childCount[0] + 1);

					entries.forEach(new Consumer<TempEntry>() {
						@Override
						public void accept(TempEntry t) {
							List<EZ> children;
							if(childCount[t.id + 1] == 0)
								children = Collections.emptyList();
							else {
								children = new ArrayList<>(childCount[t.id + 1] + 2);
								lists[t.id + 1] = children;
							} 

							EZ e = new EZ(t, t.parent_id == -1 ? null : allEntries[t.parent_id], children);
							lists[t.parent_id + 1].add(e);
							allEntries[t.id] = e;
						}
					});

					ArrayWrap<EZ> array = new ArrayWrap<>(allEntries, EZ[]::new);
					if(parsed)
						IndexHelper.writeIndex(indexPath, array);
					init(lists[0], array);
				}
			} finally {
				entries.clear();
			}
		}
	}

	public void save(Path file) throws IOException {
		if(!isModified()) return;

		ZipExtractor z = new ZipExtractor();
		z.zip(file, this, content);
		IndexHelper.writeIndex(indexPath, this.getAllEntries());
	}
	public String readContent(DataMeta meta)  {
		checkClosed();

		if(DataMeta.isEmpty(meta))
			return "";

		try(Resources r = Resources.get()) {
			StringBuilder sb = r.sb();
			content.readText(meta, r.buffer(), r.chars(), r.decoder(), sb);
			return sb.toString();
		} catch (IOException e) {
			logger.error("failed to read: {}", meta, e);
			return "";
		}
	}
	@Override
	protected IdentityHashMap<DataMeta, DataMeta> transferFrom(RootEntryZ from, List<DataMeta> metas) throws IOException {
		checkClosed();
		return ((CachedRoot)from).content.transferTo(metas, this.content);
	}
}
