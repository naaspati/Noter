package sam.apps.jbook_reader;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javafx.scene.control.TreeItem;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsSystem;

public class Utils {
	private static final Logger LOGGER = MyLoggerFactory.logger(Utils.class.getSimpleName());

	private Utils() {}

	public static String treeToString(TreeItem<String> item) {
		return treeToString(item, new StringBuilder()).toString();
	}
	private static final char[][] separator = Stream.of(" ( ", " > ", " )\n").map(String::toCharArray).toArray(char[][]::new);
	public static StringBuilder treeToString(TreeItem<String> item, StringBuilder sb) {
		if(item == null)
			return sb;

		TreeItem<String> t = item;
		sb.append(t.getValue());

		if(t.getParent().getParent() != null) {
			sb.append(separator[0]);
			List<String> list = new ArrayList<>();
			list.add(t.getValue());
			while((t = t.getParent()) != null) list.add(t.getValue());

			for (int i = list.size() - 2; i >= 0 ; i--)
				sb.append(list.get(i)).append(separator[1]);

			sb.setLength(sb.length() - 3);
			sb.append(separator[2]);
		}
		else
			sb.append('\n');

		return sb;
	}
	private static Path pluginDir;
	private static boolean pluginDirInit;
	public static Path getPluginDir(){
		if(pluginDirInit) return pluginDir;
		pluginDirInit = true;
		
		String pathS = MyUtilsSystem.lookup("plugins_dir");
		if(pathS == null) {
			LOGGER.severe("plugins_dir not set");
			return null;
		}
		Path path = Paths.get(pathS);
		if(Files.notExists(path)) {
			LOGGER.severe("plugins_dir not found: "+path.toAbsolutePath());
			return null;
		}
		return pluginDir = path;
	}

	private static ClassLoader clsLoader;
	private static boolean clsLoaderInit;

	@SuppressWarnings("unchecked")
	public static <E> E loadClass(String t) throws InstantiationException, IllegalAccessException, ClassNotFoundException, ClassCastException {
		if(clsLoaderInit && clsLoader == null) return null;
		
		if (!clsLoaderInit) {
			clsLoaderInit = true;
			if(pluginDir == null) return null;
			clsLoader = MyUtilsException.noError(() -> new URLClassLoader(new URL[] {pluginDir.toUri().toURL()}));
		}
		
		return clsLoader == null ? null : (E)clsLoader.loadClass(t).newInstance();
	}
}
