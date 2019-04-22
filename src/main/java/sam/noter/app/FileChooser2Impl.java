package sam.noter.app;

import java.io.File;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import sam.fx.popup.FxPopupShop;
import sam.myutils.Checker;
import sam.noter.api.Configs;
import sam.noter.api.FileChooser2;

interface FileChooser2Impl extends FileChooser2 {
    static final String KEY = FileChooser2Impl.class.getName().concat("last_visited");
    
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
			expectedDir = Optional.ofNullable(config().getString(KEY)).map(File::new).filter(File::isDirectory).orElse(null);

		if(expectedDir != null && expectedDir.isDirectory())
			chooser.setInitialDirectory(expectedDir);

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == Type.OPEN ? chooser.showOpenDialog(stage()) : chooser.showSaveDialog(stage());

		if(file == null)
			FxPopupShop.showHidePopup("cancelled", 1500);
		else {
			config().setString(KEY, file.getParent());
			if(onSelect != null)
				onSelect.accept(file);
		}

		return file;
	}

	FileChooser newFileChooser();
	Window stage();
	Configs config();
}
