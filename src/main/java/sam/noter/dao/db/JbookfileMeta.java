package sam.noter.dao.db;



public interface JbookfileMeta {
	String TABLE_NAME = "jbookfile";
	String TABLE_NAME_SCHEDULED_DELETE = "jbookfiletodelete";

	String ID = "id";
	String FILE = "file";
	String LAST_MODIFIED = "last_modified";
	String TABLENAME = "tablename";

	String DELETE_ON = "delete_on";


	String CREATE_TABLE_SQL = 
			"create table if not exists jbookfile (\n" + 
					"  id integer not null unique,\n" + 
					"  file text not null unique,\n" + 
					"  last_modified integer not null,\n" + 
					"  tablename text not null,\n" + 
					"  primary key(id, file)\n" + 
					") \n";

	String CREATE_TABLE_SCHEDULED_DELETE_SQL = 
			"create table if not exists jbookfiletodelete (\n" + 
					"  id integer not null,\n" + 
					"  file text not null,\n" + 
					"  last_modified integer not null,\n" + 
					"  tablename text not null,\n" + 
					"delete_on integer\n"+
					") \n";

}