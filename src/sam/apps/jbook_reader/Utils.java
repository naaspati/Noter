package sam.apps.jbook_reader;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javafx.scene.control.TreeItem;

public class Utils {
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
	public static <E> void each(Consumer<E> consumer, @SuppressWarnings("unchecked") E...elements) {
		for (E e : elements)
			consumer.accept(e);
	}
}
