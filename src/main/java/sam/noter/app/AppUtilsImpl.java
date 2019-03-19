package sam.noter.app;

import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import sam.di.AppConfig;
import sam.di.ConfigKey;
import sam.fx.popup.FxPopupShop;
import sam.myutils.Checker;

interface AppUtilsImpl extends FileChooserHelper {
	@Override
	default File chooseFile(String title, File expectedDir, String expectedFilename, Type type, Consumer<File> onSelect, ExtensionFilter...filters) {
		Objects.requireNonNull(type);

		FileChooser chooser = newFileChooser();
		chooser.setTitle(title);
		
		if(Checker.isEmpty(filters))
			chooser.getExtensionFilters().clear();
		else
			chooser.getExtensionFilters().setAll(filters);

		if(expectedDir == null || !expectedDir.isDirectory())
			expectedDir = new File(config().getConfig(ConfigKey.LAST_VISITED));

		if(expectedDir != null && expectedDir.isDirectory())
			chooser.setInitialDirectory(expectedDir);

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == Type.OPEN ? chooser.showOpenDialog(stage()) : chooser.showSaveDialog(stage());

		if(file == null)
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			config().setConfig(ConfigKey.LAST_VISITED, file.getParent());
			if(onSelect != null)
				onSelect.accept(file);
		}

		return file;
	}

	FileChooser newFileChooser();
	Window stage();
	AppConfig config();
}
