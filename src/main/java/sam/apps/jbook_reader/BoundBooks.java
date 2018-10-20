package sam.apps.jbook_reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import javafx.stage.FileChooser;
import sam.apps.jbook_reader.tabs.Tab;
import sam.config.Session;
import sam.fx.alert.FxAlert;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;

public class BoundBooks {
	private final HashMap<String, String> boundBooks = new HashMap<>();

	
	public BoundBooks() throws IOException {
		Path path = Paths.get("boundBooks.txt");
		if(Files.notExists(path)) return;
		
		Files.lines(path).forEach(s -> {
			int n = s.indexOf('\t');
			if(n < 0) return;
			boundBooks.put(s.substring(0, n), s.substring(n+1));
		});
	}
	public void openBook(Tab tab) {
		if(tab.getJbookPath() == null)
			return;
		String s = boundBooks.get(tab.getJbookPath().toString());
		if(s == null) return;
		Path p = Paths.get(s);
		if(Files.notExists(p))
			FxAlert.showErrorDialog(s, "Book File not found", null);
		else 
			FileOpenerNE.openFile(p.toFile());
	}
	
	public void bindBook(Tab tab) {
		FileChooser fc = new FileChooser();
		String s = Session.getProperty(getClass(), "recent_dir");
		File file = s == null ? null : new File(s);
		if(file != null && file.exists()) 
			fc.setInitialDirectory(file);
		
		file = fc.showOpenDialog(App.getStage());
		
		if(file == null) 
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			boundBooks.put(s, file.toString());
			Session.put(getClass(), "recent_dir", file.getParent());
			FileOpenerNE.openFile(file);
		}
	}
}
