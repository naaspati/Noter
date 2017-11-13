package sam.apps.jbook_reader;

import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import sam.properties.session.Session;

public class Editor {
	private final TextArea contentTA = new TextArea();
	private final Text titleText = new Text();
	
	public Editor() {
		if(Session.has("font.name")){
			Font font = Font.font(Session.get("font.name"), Double.parseDouble(Session.get("font.size")));
			contentTA.setFont(font);
			titleText.setFont(font);	
		}
	}
	
	public TextArea getContentNode() {
		return contentTA;
	}
	public Text getTitleNode() {
		return titleText;
	}
	public String getContent() {
		String s = contentTA.getText(); 
		return s == null || s.isEmpty() ? null : contentTA.getText();
	}
	public void setContent(String content) {
		this.contentTA.setText(content);
	}
	public String getTitle() {
		return titleText.getText();
	}
	public void setTitle(String title) {
		this.titleText.setText(title);;
	}

	public void disableIf(BooleanBinding bb) {
		contentTA.disableProperty().bind(bb);
	}

}
