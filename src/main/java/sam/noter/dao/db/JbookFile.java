package sam.noter.dao.db;
import static sam.noter.dao.db.JbookfileMeta.DELETE_ON;
import static sam.noter.dao.db.JbookfileMeta.FILE;
import static sam.noter.dao.db.JbookfileMeta.ID;
import static sam.noter.dao.db.JbookfileMeta.LAST_MODIFIED;
import static sam.noter.dao.db.JbookfileMeta.TABLENAME;
import static sam.noter.dao.db.JbookfileMeta.TABLE_NAME;
import static sam.noter.dao.db.JbookfileMeta.TABLE_NAME_SCHEDULED_DELETE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import sam.sql.sqlite.SQLiteDB;

public class JbookFile {
	
	private final int id;
	private final String file;
	private final long last_modified;
	private final String tablename;

	public JbookFile(int id,String file, long last_modified, String tablename) {
		this.id = id;
		this.file = file;
		this.last_modified = last_modified;
		this.tablename = tablename;
	}

	public JbookFile(ResultSet rs) throws SQLException {
		this.id = rs.getInt(ID);
		this.file = rs.getString(FILE);
		this.last_modified = rs.getLong(LAST_MODIFIED);
		this.tablename = rs.getString(TABLENAME);

	}

	public String getFile(){ return this.file; }
	public long getLastModified(){ return this.last_modified; }
	public String getTablename(){ return this.tablename; }
	
	public static final String FIND_BY_FILE = "SELECT * FROM "+TABLE_NAME+" WHERE "+FILE+"=";
	public static JbookFile getByFile(SQLiteDB db, String file) throws SQLException {
		return db.findFirst(FIND_BY_FILE+"'"+file.replace("'", "''")+"'", JbookFile::new);
	}
	private static final String INSERT = String.format("insert into %s(%s, %s, %s, %s) values(%%s,'%%s', %%s, '%%s')", TABLE_NAME, ID, FILE, LAST_MODIFIED, TABLENAME); 
	public void insert(SQLiteDB sqlite) throws SQLException {
		sqlite.executeUpdate(String.format(INSERT, id, file.replace("'", "''"), last_modified, tablename));
	}

	@Override
	public String toString() {
		return "JbookFile [id=" + id + ", file=" + file + ", last_modified=" + last_modified + ", tablename="
				+ tablename + "]";
	}

	public void scheduleToDelete(SQLiteDB db) throws SQLException {
		Statement s = db.getDefaultStatement();
		
		s.addBatch("INSERT into "+TABLE_NAME_SCHEDULED_DELETE+"("+String.join(",", ID, FILE, LAST_MODIFIED, TABLENAME)+  ") select * from "+TABLE_NAME+" where "+ID+"="+id);
		s.addBatch("DELETE FROM "+TABLE_NAME+" WHERE "+ID+"="+id);
		s.addBatch("UPDATE "+TABLE_NAME_SCHEDULED_DELETE+" set "+DELETE_ON+"="+(System.currentTimeMillis()+Duration.ofDays(5).toMillis())+" WHERE "+ID+"="+id);
		
		s.executeBatch();
		s.clearBatch();
	}
}

