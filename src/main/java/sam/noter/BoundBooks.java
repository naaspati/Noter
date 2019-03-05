package sam.noter;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import sam.di.ConfigKey;
import sam.di.ConfigManager;
import sam.di.ParentWindow;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.noter.tabs.Tab;

@Singleton
public class BoundBooks {
	private final HashMap<String, String> boundBooks = new HashMap<>();
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

		Files.lines(path).forEach(s -> {
			int n = s.indexOf('\t');
			if(n < 0) return;
			boundBooks.put(s.substring(0, n), s.substring(n+1));
		});
	}
	
	public String getBoundBookPath(Tab tab) {
		if(tab.getJbookPath() == null)
			return null;
		return boundBooks.get(tab.getJbookPath().toString());
	}
	public void bindBook(Tab tab) {
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
		if(tab.getTabTitle() != null)
			fc.setTitle("Book for: "+tab.getTabTitle());

		if(file == null) 
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			boundBooks.put(tab.getJbookPath().toString(), file.toString());
			configManager.setConfig(ConfigKey.RECENT_DIR, file.getParent());
			FileOpenerNE.openFile(file);
			modified = true;
			tab.setBoundBook(file);
		}
	}
	public void save() {
		if(!modified) return;
		StringBuilder sb = new StringBuilder();
		boundBooks.forEach((s,t) -> {
			if(s == null || t == null) return;
			sb.append(s).append('\t').append(t).append('\n');
		});

		try {
			StringWriter2.setText(path, sb);
			logger().debug("modified: ",path);
		} catch (IOException e) {
			logger().fatal( "failed to save: ",path, e);
		}
	}
	private Logger logger() {
		return LogManager.getLogger(getClass());
	}
}
