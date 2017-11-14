package sam.apps.jbook_reader;

import javafx.beans.binding.BooleanBinding;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCombination;

public interface Creater {
	
	public static MenuItem menuitem(String label, EventHandler<ActionEvent> action) {
		return menuitem(label, null, action);
	}
	public static MenuItem menuitem(String label, EventHandler<ActionEvent> action, BooleanBinding disable) {
		return menuitem(label, null, action, disable);
	}
	public static MenuItem menuitem(String label, KeyCombination accelerator, EventHandler<ActionEvent> action) {
		return menuitem(label, accelerator, action, null);
	}
	public static MenuItem menuitem(String label, KeyCombination accelerator, EventHandler<ActionEvent> action, BooleanBinding disable) {
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
	public static RadioMenuItem radioMenuitem(String label, EventHandler<ActionEvent> action, BooleanBinding disable) {
		return radioMenuitem(label, null, action, disable);
	}
	public static RadioMenuItem radioMenuitem(String label, KeyCombination accelerator, EventHandler<ActionEvent> action) {
		return radioMenuitem(label, accelerator, action, null);
	}
	public static RadioMenuItem radioMenuitem(String label, KeyCombination accelerator, EventHandler<ActionEvent> action, BooleanBinding disable) {
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
		Button b = new Button(null, new ImageView(iconName));
		b.getStyleClass().clear();
		b.setTooltip(new Tooltip(tooltip));

		if(action != null)
			b.setOnAction(action);

		return b;
	}

}
