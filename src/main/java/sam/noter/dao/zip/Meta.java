package sam.noter.dao.zip;

import sam.io.infile.DataMeta;

class Meta {
	static final int BYTES = DataMeta.BYTES + Integer.BYTES;

	final int id;
	DataMeta meta;

	public Meta(int id, DataMeta meta) {
		this.id = id;
		this.meta = meta;
	}
}
