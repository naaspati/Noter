package sam.noter.plugins;

import static sam.fx.popup.FxPopupShop.showHidePopup;
import static sam.myutils.Checker.isEmptyTrimmed;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import sam.fx.helpers.FxFxml;

public class LineSplitter extends Stage implements Consumer<TextArea>, InitFinalized {
	private int size;
	@FXML private TextArea inputTa;
	@FXML private TextArea outputTa;
	@FXML private Button check;
	@FXML private Button ok;
	@FXML private TextField sizeTF;

	private TextArea source;
	private final StringBuilder sb = new StringBuilder();
	private final Session SESSION = Session.getSession(LineSplitter.class);

	public LineSplitter() throws IOException {
		super(StageStyle.UTILITY);
		FxFxml.load(getClass().getResource("LineSplitter.fxml"), this, this);
		//load(this, true);

		initModality(Modality.WINDOW_MODAL);
		initOwner(Session.global().get(Stage.class));

		size = SESSION.getProperty("splitter.size", 100, s -> s == null || !s.matches("\\d+") ? 100 : Integer.parseInt(s));
		sizeTF.setText(String.valueOf(size));
		sizeTF.setUserData(String.valueOf(size));
		init();
	}

	@Override
	public void accept(TextArea content) {
		if(!content.isEditable()) {
			showHidePopup("not editable", 1500);
			return;
		}
		if(isEmptyTrimmed(content.getSelectedText())) { 
			showHidePopup("no text selected", 1500);
			return;
		}

		this.source = content;
		inputTa.setText(content.getSelectedText());
		show();
		Platform.runLater(() -> {
			checkAction(null);
			ok.requestFocus();
		});
	}

	@Override
	public void hide() {
		super.hide();
		source  = null;
		inputTa.setText(null);
		outputTa.setText(null);
	}

	@FXML
	private void checkAction(Event e) {
		String sizeText = sizeTF.getText();
		if(!Objects.equals(sizeText, sizeTF.getUserData())) {
			int size;
			try {
				size = Integer.parseInt(sizeText.trim());
				if(size < 1)
					throw new NumberFormatException();
			} catch (NumberFormatException|NullPointerException e2) {
				showHidePopup("bad size value", 2000);
				size = this.size;
				sizeTF.setText(String.valueOf(size));
			}
			if(size != this.size) { 
				SESSION.put("splitter.size", sizeText);
				this.size = size;
				sizeTF.setUserData(sizeText);
			}
		}

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
	private void removeNewLines(Event e) {
		inputTa.setText(inputTa.getText().replaceAll("\r?\n", " "));
		checkAction(null);
	} 
	@FXML
	private void okAction(Event e) {
		checkAction(null);
		source.replaceSelection(outputTa.getText());
		hide();
	}
	@Override
	protected void finalize() throws Throwable {
		finalized();
		super.finalize();
		
	}
}
