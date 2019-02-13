package sam.noter.plugins;
import java.io.IOException;
import java.util.function.Consumer;

import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import sam.config.Session;
import sam.fx.helpers.FxFxml;

public class TextReplace extends Stage implements Consumer<TextArea>, InitFinalized {
	
	public TextReplace() {
		super(StageStyle.UTILITY);
		initOwner(Session.global().get(Stage.class));
		initModality(Modality.APPLICATION_MODAL);
		
		try {
			FxFxml.load(getClass().getClassLoader().getResource(getClass().getSimpleName()+".fxml"), this, this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		sizeToScene();
		init();
	}
	
	@Override
	public void accept(TextArea t) {
		show(); 
		
	}
	
	@Override
	protected void finalize() throws Throwable {
		finalized();
		super.finalize();
	}

}
