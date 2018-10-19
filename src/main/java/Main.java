import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;

import javafx.application.Application;
import sam.apps.jbook_reader.App;
import sam.apps.jbook_reader.CmdLoader;

public class Main {

	public static void main( String[] args ) throws CmdLineException, IOException {
		if(args.length != 0)
			CmdLoader.init(args);
		
		Application.launch(App.class, new String[0]);
	}

}
