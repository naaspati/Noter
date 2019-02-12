package sam.noter;

import static sam.noter.Utils.APP_DATA;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.stage.FileChooser;
import javafx.stage.Stage;
import sam.config.Session;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.noter.tabs.Tab;
public class BoundBooks {
	private final HashMap<String, String> boundBooks = new HashMap<>();
	private static final Session SESSION = Session.getSession(BoundBooks.class);

	private boolean modified;

	public BoundBooks() throws IOException {
		Path path = getPath();
		if(Files.notExists(path)) return;

		Files.lines(path).forEach(s -> {
			int n = s.indexOf('\t');
			if(n < 0) return;
			boundBooks.put(s.substring(0, n), s.substring(n+1));
		});
	}
	private Path getPath() {
		return APP_DATA.resolve("boundBooks.txt");
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
			s = SESSION.getProperty("recent_dir");
			file = s == null ? null : new File(s);
		}

		if(file != null && file.exists()) 
			fc.setInitialDirectory(file);

		file = fc.showOpenDialog(SESSION.get(Stage.class));
		if(tab.getTabTitle() != null)
			fc.setTitle("Book for: "+tab.getTabTitle());

		if(file == null) 
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			boundBooks.put(tab.getJbookPath().toString(), file.toString());
			SESSION.put("recent_dir", file.getParent());
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

		Path p = getPath();

		try {
			StringWriter2.setText(p, sb);
			logger().debug("modified: ",p);
		} catch (IOException e) {
			logger().fatal( "failed to save: ",p, e);
		}
	}
	private Logger logger() {
		return LogManager.getLogger(getClass());
	}
}
