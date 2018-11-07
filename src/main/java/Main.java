import static sam.noter.dao.db.JbookfileMeta.FILE;
import static sam.noter.dao.db.JbookfileMeta.LAST_MODIFIED;
import static sam.noter.dao.db.JbookfileMeta.TABLENAME;
import static sam.noter.dao.db.JbookfileMeta.TABLE_NAME;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Arrays;

import org.kohsuke.args4j.CmdLineException;

import javafx.application.Application;
import sam.myutils.MyUtilsException;
import sam.noter.App;
import sam.noter.dao.db.DB;
import sam.noter.dao.db.JbookFile;
import sam.noter.dao.db.JbookfileMeta;
import sam.string.StringUtils;

public class Main {

	public static void main( String[] args ) throws CmdLineException, IOException {
		System.out.println(StringUtils.join("insert into ", TABLE_NAME, "(", FILE, ",", LAST_MODIFIED, ",", TABLENAME, ") values(", "file",",", "last_modified",",", "tablename", ")"));
		
		DB db = DB.getInstance();
		try {
			JbookFile file = db.file(new File(args[0]));
			System.out.println(file);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		db.close();
		System.exit(0);
		
		// print(args);
		Application.launch(App.class, args);
	}

	private static void print(String[] args) {
		MyUtilsException.noError(() -> Files.createTempFile(null, null));
		
		System.out.println(new File(".").getAbsolutePath());
		System.out.println();
		for (String s : Arrays.asList("session_file","dynamic.menus.file","plugins_dir")) 
			System.out.println(s+"  "+System.getenv(s));

		System.out.println();
		for (String s : Arrays.asList("java.io.tmpdir","java.util.logging.config.file")) 
			System.out.println(s+"  "+System.getProperty(s));
		
		System.out.println();
		System.out.println(Arrays.toString(args));
		System.exit(0);
	}

}
