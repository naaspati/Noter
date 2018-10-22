package sam.noter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import sam.myutils.System2;

public class Utils {
	public static final Path APP_DATA = Optional.ofNullable(System2.lookup("app_data")).map(Paths::get).orElse(Paths.get("app_data"));

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

	public static File getFile(String title, String suggestedName) {
		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));

		final Path p = APP_DATA.resolve("last-visited-folder.txt");

		String path;
		try {
			path = Files.exists(p) ? new String(Files.readAllBytes(p)) : null;
		} catch (IOException e) {
			path = null;
		}

		chooser.setInitialFileName(suggestedName);
		File file = path == null ? null : new File(path);
		if(file != null && file.exists())
			chooser.setInitialDirectory(file);
		else
			chooser.setInitialDirectory(new File("."));
		file = suggestedName == null ?  chooser.showOpenDialog(App.getStage()) : chooser.showSaveDialog(App.getStage());

		if(file != null) {
			try {
				Files.write(p, file.getParent().toString().replace('\\', '/').getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {}
		}

		return file;
	}
	

}
