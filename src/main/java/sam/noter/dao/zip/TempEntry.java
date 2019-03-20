package sam.noter.dao.zip;

import java.util.Objects;

import org.json.JSONObject;

import sam.io.infile.DataMeta;
import sam.myutils.ThrowException;

class TempEntry {
	private DataMeta meta;
	final int id, parent_id, order;
	final long lastmodified;
	private String title;
	
	public TempEntry(int id, int parent_id, int order, long lastmodified) {
		this.id = id;
		this.parent_id = parent_id;
		this.order = order;
		this.lastmodified = lastmodified;
	}

	public TempEntry(int id, int parent_id, int order, long lastmodified, String title) {
		this(id, parent_id, order, lastmodified);
		this.title = Objects.requireNonNull(title);
	}

	@Override
	public String toString() {
		return new JSONObject()
				.put("id", id)
				.put("parent_id", parent_id)
				.put("order", order)
				.put("lastmodified", lastmodified)
				.put("title", title)
				.put("meta", meta == null ? null : meta.toString())
				.toString(4);
	}

	public DataMeta meta() {
		return meta;
	}
	public void setMeta(DataMeta d) {
		this.meta = d;
	}
	public void setTitle(String title) {
		if(this.title != null)
			ThrowException.illegalAccessError();
		this.title = title;
	}
	public String title() {
		return title;
	}
}