package sam.noter.dao.zip;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.myutils.ThrowException;
import sam.nopkg.Resources;
import sam.noter.dao.zip.RootEntryZ.EZ;

abstract class Cache extends Meta {
	private static final Logger logger = LoggerFactory.getLogger(Cache.class);
	
	private final Path indexPath, contentPath;
	private TextInFile content;
	private List<TempEntry> entries;
	
	public Cache(Meta meta, Path saveDir) throws IOException {
		super(meta);
		
		this.indexPath = saveDir.resolve(id+".index");
 		this.contentPath = saveDir.resolve(id+".content");
 		
 		load();
	}
	
	protected abstract void saveMeta() throws IOException;

	private ArrayList<TempEntry> parseZip() throws IOException {
		ArrayList<TempEntry> entries = new ZipFileHelper().parseZip(source(), content);
		
		IndexHelper.writeIndex(indexPath, entries);
		updateLastmodified();
		saveMeta();
		
		return entries;
	}
	
	private void updateLastmodified() {
		super.setLastModified(id);
	}
	@Override
	protected void setLastModified(long lastModified) {
		ThrowException.illegalAccessError();
	}
	@Override
	public void setSource(Path jbook_path) {
		ThrowException.illegalAccessError();
	}
	
	public void reload() throws IOException {
		deleteCache();
		load();
	}
	private void deleteCache() throws IOException {
		Files.deleteIfExists(indexPath);
		Files.deleteIfExists(contentPath);
	}

	private void load() throws IOException {
		if(Files.notExists(indexPath) || Files.notExists(contentPath)) {
 			deleteCache();
 			
 			this.content = new TextInFile(contentPath, true);
 			logger.debug("parsing zip: {}", source());
 			this.entries = parseZip();
 		} else {
 			this.content = new TextInFile(contentPath, false);
 			this.entries = IndexHelper.readIndex(indexPath);
 			logger.debug("reading index: {}", indexPath);
 		}
	}
	public ArrayWrap<EZ> getEntries(RootEntryZ root, ArrayWrap<EZ> entries) {
		// TODO Auto-generated method stub
		return null;
	}

	public void save(Path file) {
		// TODO Auto-generated method stub
		
	}

	public IdentityHashMap<DataMeta, DataMeta> transferFrom(Cache cache, List<DataMeta> metas) throws IOException {
		return cache.content.transferTo(metas, this.content);
	}

	public String readContent(DataMeta meta) throws IOException {
		if(DataMeta.isEmpty(meta))
			return "";
		
		try(Resources r = Resources.get()) {
			StringBuilder sb = r.sb();
			content.readText(meta, r.buffer(), r.chars(), r.decoder(), sb);
			return sb.toString();
		}
	}
}
