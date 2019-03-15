package sam.noter;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import sam.di.ConfigKey;
import sam.di.ConfigManager;
import sam.di.ParentWindow;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.noter.dao.api.IRootEntry;
import sam.tsv.TsvMap;

@Singleton
public class BoundBooks {
	private Map<String, String> boundBooks;
	private boolean modified;
	private final ConfigManager configManager;
	private final Window parent;
	private final Path path;

	@Inject
	public BoundBooks(ConfigManager configManager, @ParentWindow Window parent) throws IOException {
		this.configManager = configManager;
		this.parent = parent;
		this.path = configManager.appDir().resolve("boundBooks.txt");
		
		if(Files.notExists(path)) 
			return;
		
		if(Files.exists(path))
			boundBooks = TsvMap.parse(path);
	}
	
	public String getBoundBookPath(IRootEntry tab) {
		if(tab.getJbookPath() == null)
			return null;
		return boundBooks.get(tab.getJbookPath().toString());
	}
	public void bindBook(IRootEntry tab) {
		FileChooser fc = new FileChooser();
		String s = getBoundBookPath(tab);
		File file;

		if(s != null) {
			file = new File(s).getParentFile();
			fc.setInitialFileName(file.getName());
		} else {
			s = configManager.getConfig(ConfigKey.RECENT_DIR);
			file = s == null ? null : new File(s);
		}

		if(file != null && file.exists()) 
			fc.setInitialDirectory(file);

		file = fc.showOpenDialog(parent);
		if(tab.getTitle() != null)
			fc.setTitle("Book for: "+tab.getTitle());

		if(file == null) 
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			boundBooks.put(tab.getJbookPath().toString(), file.toString());
			configManager.setConfig(ConfigKey.RECENT_DIR, file.getParent());
			FileOpenerNE.openFile(file);
			modified = true;
			// will be removed 
			// tab.setBoundBook(file);
		}
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
