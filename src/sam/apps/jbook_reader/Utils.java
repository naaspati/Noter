package sam.apps.jbook_reader;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.scene.control.TreeItem;

public class Utils {
	private Utils() {}
	
	public static String treeToString(TreeItem<String> item) {
		return treeToString(item, new StringBuilder()).toString();
	}
	private static char[] separator = {' ', '>', ' '};
	public static StringBuilder treeToString(TreeItem<String> item, StringBuilder sb) {
		if(item == null)
			return sb;

		List<String> list = new ArrayList<>();
		TreeItem<String> t = item;
		list.add(t.getValue());
		sb.append(t.getValue()).append("\n   ");

		while((t = t.getParent()) != null) list.add(t.getValue());

		for (int i = list.size() - 2; i >= 0 ; i--)
			sb.append(list.get(i)).append(separator);

		sb.setLength(sb.length() - 3);
		sb.append('\n');
		return sb;
	}
	public static <E> void each(Consumer<E> consumer, @SuppressWarnings("unchecked") E...elements) {
		for (E e : elements)
			consumer.accept(e);
	}
}
