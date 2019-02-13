package sam.noter.plugins;
import java.util.function.Consumer;

import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Modality;
import javafx.stage.Stage;
import sam.config.Session;
import sam.fx.popup.FxPopupShop;
import sam.reference.WeakAndLazy;

public class TextInsert  implements Consumer<TextArea>, InitFinalized {

	private WeakAndLazy<StringBuilder> wsb = new WeakAndLazy<>(StringBuilder::new);
	private String value = "";
	
	public TextInsert() {
		init();
	}

	@Override
	public void accept(TextArea source) {
		TextInputDialog dialog = new TextInputDialog(value);
		dialog.setTitle("text to insert");
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.initOwner(Session.global().get(Stage.class));
		dialog.setHeaderText("text to insert");

		String text = dialog.showAndWait().orElse(null);

		if(text == null)
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			if(text.isEmpty()) return;
			value = text;
			char textC = ' ';
			if(text.length() == 1) {
				textC = text.charAt(0);
				text = null;
			}
			IndexRange range = source.getSelection();
			int first = range.getStart();
			int last = range.getEnd();
			if(last == first)
				last = source.getLength();

			int start = coalesce(source.getText()).lastIndexOf('\n', first);
			start = start == -1 ? 0 : start + 1; 
			String selected = coalesce(source.getText(start, last));
			int length = first - start;
			StringBuilder sb = wsb.get();
			sb.setLength(0);
			sb.ensureCapacity(selected.length()*(text == null ? 1 : text.length())*10);

			int n = 0;
			for (int i = 0; i < selected.length(); i++) {
				char c = selected.charAt(i);
				if(c == '\n') 
					n = -1;
				if(n == length) {
					if(text == null)
						sb.append(textC);
					else
						sb.append(text);
				}
				sb.append(c);
				n++;
			} 
			source.replaceText(start, last, sb.toString());
		}
	}
	private String coalesce(String text) {
		return text == null ? "" : text;
	}

	@Override
	protected void finalize() throws Throwable {
		finalized();
		super.finalize();
	}

}
