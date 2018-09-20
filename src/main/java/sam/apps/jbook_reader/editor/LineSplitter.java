package sam.apps.jbook_reader.editor;

import java.io.IOException;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.apps.jbook_reader.Main;
import sam.config.Session;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;

public class LineSplitter extends Stage {
	private int size;
	@FXML private TextArea inputTa;
	@FXML private TextArea outputTa;
	@FXML private Button check;
	@FXML private Button ok;
	@FXML private TextField sizeTF;
	
	private TextArea source;

	public LineSplitter() throws IOException {
		super(StageStyle.UTILITY);
		FxFxml.loadFxml(ClassLoader.getSystemResource("LineSplitter.fxml"), this, this);
		
		initModality(Modality.WINDOW_MODAL);
		initOwner(Main.getStage());

		size = Session.get("splitter.size", 150, Integer::parseInt);
		sizeTF.setText(String.valueOf(size));
	}
	
	public void show(TextArea source) {
		this.source = source;
		inputTa.setText(source.getSelectedText());
		show();
		Platform.runLater(() -> checkAction(null));
	}
	
	@Override
	public void hide() {
		super.hide();
		source  = null;
		inputTa.setText(null);
		outputTa.setText(null);
	}
	
	private final StringBuilder sb = new StringBuilder();
	
	@FXML
	private void checkAction(Event e) {
		int size;
		try {
			size = Integer.parseInt(sizeTF.getText());
			if(size < 1)
				throw new NumberFormatException();
		} catch (NumberFormatException e2) {
			FxPopupShop.showHidePopup("bad size value", 2000);
			size = this.size;
			sizeTF.setText(String.valueOf(size));
		}
		
		if(size != this.size)
			 Session.put("splitter.size", String.valueOf(size));
		
		sb.setLength(0);
		
		String s = inputTa.getText();
		int start = 0;
		for (int i = 0; i < s.length(); i++) {
			if(s.charAt(i) == ' ' && i - start >= size && i != 0) {
				sb.append(s, start, i).append('\n');
				start = i;
			}
		}
		
		if(start < s.length() - 1)
			sb.append(s, start, s.length());
		
		outputTa.setText(sb.toString());
	}
	@FXML
	private void okAction(Event e) {
		checkAction(null);
		source.replaceSelection(outputTa.getText());
		hide();
	}
}
