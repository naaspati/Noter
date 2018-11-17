package sam.extra;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import sam.myutils.MyUtilsException;
import sam.myutils.System2;

public interface EnvKeys {
	
	/**
	 * working dir for app
	 */
	final Path APP_DATA = Optional.ofNullable(System2.lookupAny("app_data", "APP_DATA","app.data", "APP.DATA"))
			.map(Paths::get)
			.orElseGet(() -> MyUtilsException.noError(() -> Paths.get(ClassLoader.getSystemResource(".").toURI()).resolve("app_data")));;
	/**
	 * dir to watch for files with commands to open files
	 */
	final boolean OPEN_CMD_ENABLE = System2.lookupBoolean("noter.open.cmd.enable", true);
	final Path OPEN_CMD_DIR = APP_DATA.resolve("cmd_open");
	final String DYNAMIC_MENUS_FILE = "dynamic.menus.file";
	final String PLUGIN_DIR = "plugins_dir";
	final String DEFAULT_SAVE_DIR = "default.save.dir";
	final boolean ENABLE_FILE_LOOKUP_OPEN_CACHE = System2.lookupBoolean("file.lookup.open.cache.enable", false);
}
