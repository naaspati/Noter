package sam.noter;

import java.io.File;
import java.util.Objects;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import sam.fx.clipboard.FxClipboard;
import sam.fx.popup.FxPopupShop;
import sam.noter.dao.api.IEntry;

public interface Utils2 {

	public static void copyToClipboard(String s) {
		FxClipboard.setString(s);
		FxPopupShop.showHidePopup(s, 2000);
	}
	public static void fx(Runnable runnable) {
		Platform.runLater(runnable);
	}
	
	public static void toTreeString(IEntry n, StringBuilder  sb) {
		toTreeString(n.getParent(), sb);
		
		if(n != null)
			sb.append(n.getTitle()).append(" > ");
	}
}
