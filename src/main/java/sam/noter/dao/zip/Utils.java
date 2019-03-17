package sam.noter.dao.zip;

import java.util.Collection;

interface Utils {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void ensureIndex(Collection col, int index) {
		while(col.size() <= index) { col.add(null); }
	}

}
