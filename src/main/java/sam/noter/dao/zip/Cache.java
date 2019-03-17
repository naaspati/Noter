package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.READ;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.collection.CollectionUtils;
import sam.io.BufferSupplier;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.io.serilizers.StringIOUtils;
import sam.nopkg.Resources;
import sam.noter.dao.zip.RootEntryZ.EZ;
import sam.noter.dao.zip.ZipFileHelper.TempEntry;
import sam.string.StringSplitIterator;

class Cache {
	private static final Logger logger = LoggerFactory.getLogger(Cache.class);
	
	private final Meta meta;
	private final Path indexPath, contentPath;
	private final TextInFile content;
	
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
		ArrayList<TempEntry> entries = new ZipFileHelper().parseZip(meta.path(), content);
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
