package sam.fxml;

import javafx.beans.NamedArg;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;

public class Button2 extends Button {
	private static final String iconDir = ClassLoader.getSystemResource("icons").toString()+"/";
	
	public Button2(@NamedArg("tip") String tip, @NamedArg("icon") String icon) {
		this(tip, icon, null);
	}
	public Button2(String tip, String icon, EventHandler<ActionEvent> action) {
		super(null, new ImageView(iconDir+icon));
		
		getStyleClass().clear();
		
		if(tip != null)
			setTooltip(new Tooltip(tip));
		if(action != null) 
			setOnAction(action);
	}
}
