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
import sam.noter.dao.zip.RootEntryZ.EZ;

abstract class Cache {
	private static final Logger logger = LoggerFactory.getLogger(Cache.class);
	
	private final Meta meta;
	private final Path indexPath, contentPath;
	private final TextInFile content;
	private ArrayList<TempEntry> entries;
	
	public Cache(Meta meta, Path saveDir) throws IOException {
		this.meta = meta;
 		this.indexPath = saveDir.resolve(meta.id+".index");
 		this.contentPath = saveDir.resolve(meta.id+".content");
 		
 		if(Files.notExists(indexPath) || Files.notExists(contentPath)) {
 			Files.deleteIfExists(indexPath);
 			Files.deleteIfExists(contentPath);
 			this.content = new TextInFile(contentPath, true);
 			parseZip();
 		} else {
 			this.content = new TextInFile(contentPath, false);
 			load();
 		}
	}

	private void parseZip() throws IOException {
		this.entries = new ZipFileHelper().parseZip(meta.path(), content);
		if(!entries.isEmpty())
			IndexHelper.writeIndex(indexPath, entries);
		
		// TODO Auto-generated method stub
		
	}

	private void load() {
		// TODO Auto-generated method stub
	}

	public void close() {
		// TODO Auto-generated method stub
		
	}
	public Path source() {
		return meta.path();
	}
	public ArrayWrap<EZ> getEntries(RootEntryZ rootEntryZ, ArrayWrap<EZ> entries) {
		// TODO Auto-generated method stub
		return null;
	}

	public void save(Path file) {
		// TODO Auto-generated method stub
		
	}

	public IdentityHashMap<DataMeta, DataMeta> transfer(Cache cache, List<DataMeta> dm) {
		// TODO Auto-generated method stub
		return null;
	}

	public String readContent(DataMeta meta) {
		// TODO Auto-generated method stub
		return null;
	}

}
