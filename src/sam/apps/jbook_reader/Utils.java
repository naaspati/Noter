package sam.apps.jbook_reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.text.Text;

public interface Utils {
	public static String treeToString(TreeItem<String> item) {
		return treeToString(item, new StringBuilder()).toString();
	} 
	public static final char[] separator = {' ', '>', ' '};
	public static StringBuilder treeToString(TreeItem<String> item, StringBuilder sb) {
		if(item == null)
			return sb;

		List<String> list = new ArrayList<>();
		TreeItem<String> t = item;
		list.add(t.getValue());

		while((t = t.getParent()) != null) list.add(t.getValue());

		for (int i = list.size() - 2; i >= 0 ; i--)
			sb.append(list.get(i)).append(separator);

		sb.setLength(sb.length() - 3);
		return sb;
	}
	public static MenuItem menuitem(String label, EventHandler<ActionEvent> action) {
		return menuitem(label, null, action);
	}
	public static MenuItem menuitem(String label, EventHandler<ActionEvent> action, ObservableValue<? extends Boolean> disable) {
		return menuitem(label, null, action, disable);
	}
	public static MenuItem menuitem(String label, KeyCodeCombination accelerator, EventHandler<ActionEvent> action) {
		return menuitem(label, accelerator, action, null);
	}
	public static MenuItem menuitem(String label, KeyCodeCombination accelerator, EventHandler<ActionEvent> action, ObservableValue<? extends Boolean> disable) {
		MenuItem mi = new MenuItem(label);

		if(accelerator != null)
			mi.setAccelerator(accelerator);
		if(action != null)
			mi.setOnAction(action);
		if(disable != null)
			mi.disableProperty().bind(disable);

		return mi;
	}
	public static RadioMenuItem radioMenuitem(String label, EventHandler<ActionEvent> action) {
		return radioMenuitem(label, null, action);
	}
	public static RadioMenuItem radioMenuitem(String label, EventHandler<ActionEvent> action, ObservableValue<? extends Boolean> disable) {
		return radioMenuitem(label, null, action, disable);
	}
	public static RadioMenuItem radioMenuitem(String label, KeyCodeCombination accelerator, EventHandler<ActionEvent> action) {
		return radioMenuitem(label, accelerator, action, null);
	}
	public static RadioMenuItem radioMenuitem(String label, KeyCodeCombination accelerator, EventHandler<ActionEvent> action, ObservableValue<? extends Boolean> disable) {
		RadioMenuItem mi = new RadioMenuItem(label);

		if(accelerator != null)
			mi.setAccelerator(accelerator);
		if(action != null)
			mi.setOnAction(action);
		if(disable != null)
			mi.disableProperty().bind(disable);

		return mi;
	}
	public static Button button(String tooltip, String iconName, EventHandler<ActionEvent> action) {
		Button b = new Button(null, iconName == null ? null : new ImageView(iconName));

		b.getStyleClass().clear();
		b.setTooltip(new Tooltip(tooltip));

		if(action != null)
			b.setOnAction(action);

		return b;
	}
	public static void addClass(Node node, String clazz) {
		node.getStyleClass().add(clazz);
	}
	public static void setClass(Node n, String...className) {
		ObservableList<String> l = n.getStyleClass();
		l.clear();
		l.addAll(className);
	}
	public static void toggleClass(Node node, String clazz, boolean add) {
		if(add) {
			if(!node.getStyleClass().contains(clazz))
				node.getStyleClass().add(clazz);
		}
		else
			node.getStyleClass().remove(clazz);
	}
	public static KeyCodeCombination combination(KeyCode code, Modifier...modifiers) {
		return new KeyCodeCombination(code, modifiers);
	}
	public static <E> void each(Consumer<E> consumer, @SuppressWarnings("unchecked") E...elements) {
		for (E e : elements)
			consumer.accept(e);
	}
	public static String createBanner(String text, int length, char symbol){
		if(text == null)
			text = "null";

		StringBuilder b = new StringBuilder();
		char[] symbols = new char[length];
		Arrays.fill(symbols, symbol);

		b.append(symbols);
		b.append("\n");

		int half = (length - text.length() - 2)/2;
		boolean lengthBool = half > 2;

		if(lengthBool)
			for (int i = 0; i < half; i++) b.append(symbol);

		if(lengthBool){
			b.append(' ')
			.append(text)
			.append(' ');
		}
		else
			b.append(text);


		if(lengthBool)
			for (int i = half; i < length - text.length() - 2; i++) b.append(symbol);

		b.append("\n");
		b.append(symbols);

		return b.toString();
	}
	public static Text text(String string) {
		return new Text(string);
	}
}
