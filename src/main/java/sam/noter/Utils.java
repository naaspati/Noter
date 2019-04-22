package sam.noter;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.popup.FxPopupShop;
import sam.io.serilizers.StringIOUtils;
import sam.noter.dao.api.IEntry;
import sam.reference.WeakAndLazy;
import sam.string.StringWriter2;

public interface Utils {
	public static Logger logger(@SuppressWarnings("rawtypes") Class cls) {
		return LoggerFactory.getLogger(cls);
	}

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
	static final WeakAndLazy<StringBuilder> sb = new WeakAndLazy<>(StringBuilder::new);
	public static String toTreeString(EntryTreeItem e, boolean b) {
		StringBuilder sb = Utils.sb.get();
		sb.setLength(0);
				
		toTreeString(e.getEntry(), sb);
		return sb.toString();
	}
	public static void writeTextHandled(String text, Path path) {
		try {
		    StringIOUtils.write(text, path);
		} catch (IOException e2) {
			FxAlert.showErrorDialog(path, "failed to save", e2);
		}
	}

	public static String toString(int i) {
		// FIXME optimize
		return Integer.toString(i);
	}
}
