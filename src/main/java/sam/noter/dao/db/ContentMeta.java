package sam.noter.dao.db;



public interface ContentMeta {
	String ID = "id";
	String PARENT_ID = "parent_id";
	String TITLE = "title";
	String CONTENT = "content";
	String LAST_MODIFIED = "last_modified";

	public static String createTableSql(JbookFile file) {
		return "create table "+file.getTablename()+" (\n"+
				"  id integer not null  unique, \n"+
				"  parent_id integer not null, \n"+
				"  title text,\n"+
				"  content text,\n"+
				"  last_modified integer not null, \n"+
				"  PRIMARY KEY(id, parent_id)\n"+
				")\n";
	} ;

}