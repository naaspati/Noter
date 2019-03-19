import static java.nio.file.StandardOpenOption.CREATE;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javafx.application.Application;
import sam.config.LoadConfig;
import sam.fx.helpers.ErrorApp;
import sam.myutils.System2;
import sam.noter.app.App;

public class Main {
	public static void main( String[] args ) throws Exception {
		LoadConfig.load();
		
		final Path appDir = Paths.get("app_data");
		if(!Files.isDirectory(appDir)) {
			error("app_data dir not found", null);
			return;
		}
		
		String opencmd = System2.lookup("noter.open.by.cmd.dir");

		FileChannel fc;
		try {
			fc = FileChannel.open(appDir.resolve("noter.lock"));
			FileLock lock = fc.tryLock();
			if(lock == null) {
				if(args.length == 0 || opencmd == null) {
					error("Only One Instance Allowed", null);
				} else {
					Path p = Paths.get(opencmd);
					Files.createDirectories(p);
					Files.write(p.resolve(String.valueOf(System.currentTimeMillis())), Arrays.asList(args), CREATE);
				}
				fc.close();
				return;
			}
		} catch (Throwable e) {
			error("Failed to accuire lock", e);
			return;
		}
		
		if(opencmd != null)
			System.getProperties().put("OPEN_CMD_DIR", Paths.get(opencmd));
		System.getProperties().put("app_data", appDir);
		Application.launch(App.class, args);
	}

	private static void error(String title, Throwable error) {
		ErrorApp.set(title, error);
		Application.launch(ErrorApp.class, new String[0]);
	}
}
