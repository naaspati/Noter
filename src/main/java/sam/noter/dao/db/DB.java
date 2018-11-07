package sam.noter.dao.db;

import static sam.noter.dao.db.JbookfileMeta.*;
import static sam.noter.dao.db.JbookfileMeta.CREATE_TABLE_SQL;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

import sam.logging.MyLoggerFactory;
import sam.noter.Utils;
import sam.sql.sqlite.SQLiteDB;

public class DB {
	private static final Logger LOGGER = MyLoggerFactory.logger(DB.class.getSimpleName());
	
	private static volatile DB INSTANCE;

	public static DB getInstance() {
		if (INSTANCE != null)
			return INSTANCE;

		synchronized (DB.class) {
			if (INSTANCE != null)
				return INSTANCE;

			INSTANCE = new DB();
			return INSTANCE;
		}
	}
	
	private final SQLiteDB sqlite;
	private int currentId0 = -1;

	private DB() {
		try {
			sqlite = new SQLiteDB(Utils.APP_DATA.resolve("noter.db"), true);
			sqlite.executeUpdate(CREATE_TABLE_SQL);
			sqlite.executeUpdate(CREATE_TABLE_SCHEDULED_DELETE_SQL);
			
			sqlite.commit();

			/**
			 *  TODO
			 * 			Utils.addOnStop(() -> {
				try {
					sqlite.close();
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			});
			 */

		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public JbookFile file(File file) throws SQLException, IOException {
		if(!file.exists())
			throw new FileNotFoundException(file.toString());

		String s = path(file);
		long lastModified = file.lastModified();
		
		JbookFile jf = JbookFile.getByFile(sqlite, s);
		if(jf == null) {
			LOGGER.fine(() -> "NO JbookFile found in db, for file: "+file+" -> "+s);
		} else if(lastModified != jf.getLastModified()) {
			jf.scheduleToDelete(sqlite);
			JbookFile jf2 = jf;
			LOGGER.fine(() -> "SCHEDULED TO DELETE: "+jf2+" [new: "+lastModified+", old: "+jf2.getLastModified()+"]");
			jf = null;
		}
		
		if(jf == null) {
			int id = id();
			String tablename = "tbl_"+id+"_"+lastModified;
			jf = new JbookFile(id, s, lastModified, tablename);
			
			jf.insert(sqlite);
			sqlite.executeUpdate(ContentMeta.createTableSql(jf));
			sqlite.commit();
		}
		return jf;
	}

	private int id() throws SQLException {
		if(currentId0 == -1) {
			Integer m = sqlite.findFirst("SELECT max("+ID+") from "+TABLE_NAME, rs -> rs.getInt(1));
			currentId0 = m == null ? 0 : m+1;	
		}
		return currentId0++;
	}

	private String path(File file) throws IOException {
		return file.getCanonicalPath().replace('\\', '/');
	}
	public void close(){
		try {
			sqlite.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
