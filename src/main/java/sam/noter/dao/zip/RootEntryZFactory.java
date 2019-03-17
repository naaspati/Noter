package sam.noter.dao.zip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import static java.nio.file.StandardOpenOption.*;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import sam.collection.CollectionUtils;
import sam.di.AppConfig;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.infile.DataMeta;
import sam.io.serilizers.StringIOUtils;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Junk;
import sam.nopkg.Resources;
import sam.noter.Utils;
import sam.noter.dao.RootEntryFactory;

@Singleton
public class RootEntryZFactory implements RootEntryFactory, AutoCloseable {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	
	private final Logger logger;
	private final Path mydir;
	private final Path metasPath;
	private final ArrayList<Meta> metas;

	@Inject
	public RootEntryZFactory(AppConfig config) throws IOException {
		singleton.init();

		this.logger = Utils.logger(getClass());
		this.mydir = config.tempDir().resolve(getClass().getName());
		this.metasPath = mydir.resolve("app.index");
		this.metas = new ArrayList<>();
		
		if(Files.notExists(metasPath)) 
			FilesUtilsIO.deleteDir(mydir);
		
		Files.createDirectories(mydir);
		if(Files.exists(metasPath))
			MetaSerializer.read(metas, metasPath);
		
	}
	@Override
	public void close() throws Exception {
		//FIXME metas.close();
	}

	@Override
	public RootEntryZ create(Path path) throws Exception {
		if(Files.exists(path))
			throw new IOException("file already exists: "+path);
		
		return create0(path.normalize().toAbsolutePath());
	}
	private RootEntryZ create0(Path path) throws Exception {
		//TODO
		return Junk.notYetImplemented(); // return new RootEntryZ(m, path, this);
	}

	@Override
	public RootEntryZ load(Path path) throws Exception {
		if(!Files.isRegularFile(path))
			throw new IOException("file not found: "+path);
		
		path =  path.normalize().toAbsolutePath();
		Meta meta = Junk.notYetImplemented();  // TODO metas.get(path);
		
		if(meta == null)
			return create0(path);
		
		if(meta.lastModified() != path.toFile().lastModified()) {
			logger.debug("RESET CACHE: because: meta.lastModified({}) != path.toFile().lastModified({}),  path: {}", meta.lastModified(), path.toFile().lastModified(), path);
			return create0(path);
		}
		
		return Junk.notYetImplemented(); //TODO return new RootEntryZ(meta, path, this);
	}

	public void close(RootEntryZ root) {
		// TODO Auto-generated method stub
	}

	public ArrayWrap<EntryZ> getEntries(RootEntryZ root) {
		// TODO Auto-generated method stub
		return null;
	}

	public void save(RootEntryZ root, Path file) {
		// TODO Auto-generated method stub
	}
}
