package sam.noter;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.di.Injector;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.Checker;
import sam.nopkg.EnsureSingleton;
import sam.noter.api.Configs;
import sam.noter.api.FileChooser2;
import sam.noter.dao.api.IRootEntry;
import sam.tsv.TsvMap;

@Singleton
public class BoundBooks {
	private static final Logger logger = LoggerFactory.getLogger(BoundBooks.class);
	
	private static final EnsureSingleton singlton = new  EnsureSingleton();
	{
		singlton.init();
	}

	private Map<String, String> boundBooks;
	private boolean modified = false;
	private Path path;
	private final Injector injector;

	@Inject
	public BoundBooks(Injector injector) throws IOException {
		this.injector = injector;
	}

	private void load() {
		if(path != null)
			return;

		Configs config = injector.instance(Configs.class);
		this.path = config.appDataDir().resolve("boundBooks.txt");

		if(Files.notExists(path)) 
			return;

		if(Files.exists(path)) {
			try {
				boundBooks = TsvMap.parse(path);
				logger.debug("loaded: {}", path);
			} catch (IOException e) {
				logger.error("failed to load: {}", path, e);
				boundBooks = new HashMap<>();
			}	
		}
	}

	public String getBoundBookPath(IRootEntry tab) {
		load();
		
		if(tab.getJbookPath() == null)
			return null;
		
		return boundBooks.get(tab.getJbookPath().toString());
	}
	public void bindBook(IRootEntry tab) {
		String s = getBoundBookPath(tab);
		File file = s == null ? null : new File(s);
		File parent = file == null ? null : file.getParentFile();

		if(Checker.notExists(parent)) {
			file =  null;
			parent = null;
		} 
		
		FileChooser2 fc = injector.instance(FileChooser2.class);
		fc.chooseFile("Book for: "+tab.getTitle(), parent, file == null ? null : file.getName(), FileChooser2.Type.OPEN, f -> {
			boundBooks.put(tab.getJbookPath().toString(), f.toString());
			injector.instance(Configs.class).setString("RECENT_DIR", f.getParent());
			FileOpenerNE.openFile(f);
			modified = true;	
		});
		
	}
	public void save() {
		if(!modified) return;

		try {
			TsvMap.save(path, boundBooks);
			logger().debug("modified: ",path);
			modified = false;
		} catch (IOException e) {
			logger().error( "failed to save: ",path, e);
		}
	}
	private Logger logger() {
		return Utils.logger(getClass());
	}
}
