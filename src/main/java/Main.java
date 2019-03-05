import static sam.noter.EnvKeys.APP_DATA;
import static sam.noter.EnvKeys.OPEN_CMD_DIR;
import static sam.noter.EnvKeys.OPEN_CMD_ENABLE;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import javafx.application.Application;
import sam.config.LoadConfig;
import sam.fx.helpers.ErrorApp;
import sam.noter.app.App;
public class Main {
	public static void main( String[] args ) throws Exception {
		LoadConfig.load();

		FileChannel fc;
		try {
			fc = FileChannel.open(APP_DATA.resolve("noter.lock"));
			FileLock lock = fc.tryLock();
			if(lock == null) {
				if(args.length == 0 || !OPEN_CMD_ENABLE) {
					error("Only One Instance Allowed", null);
				} else {
					Files.createDirectories(OPEN_CMD_DIR);
					Files.write(OPEN_CMD_DIR.resolve(String.valueOf(System.currentTimeMillis())), Arrays.asList(args), StandardOpenOption.CREATE);
				}
				fc.close();
				return;
			}
		} catch (Throwable e) {
			error("Failed to accuire lock", e);
			return;
		}

		Files.createDirectories(OPEN_CMD_DIR);
		Application.launch(App.class, args);
	}

	private static void error(String title, Throwable error) {
		ErrorApp.set(title, error);
		Application.launch(ErrorApp.class, new String[0]);
	}
}
