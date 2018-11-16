package sam.extra;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import sam.myutils.MyUtilsException;
import sam.myutils.System2;

public class EnvKeys {
	
	/**
	 * working dir for app
	 */
	public static final Path APP_DATA = Optional.ofNullable(System2.lookupAny("app_data", "APP_DATA","app.data", "APP.DATA"))
			.map(Paths::get)
			.orElseGet(() -> MyUtilsException.noError(() -> Paths.get(ClassLoader.getSystemResource(".").toURI()).resolve("app_data")));;
	/**
	 * dir to watch for files with commands to open files
	 */
	public static final boolean OPEN_CMD_ENABLE = "false".equals(Optional.ofNullable(System2.lookup("noter.open.cmd.enable")).map(s -> s.trim().toLowerCase()).orElse(null)) ? false : true;
	public static final Path OPEN_CMD_DIR = APP_DATA.resolve("cmd_open");
	public static final String DYNAMIC_MENUS_FILE = "dynamic.menus.file";
	public static final String PLUGIN_DIR = "plugins_dir";
	public static final String DEFAULT_SAVE_DIR = "default.save.dir";
}
