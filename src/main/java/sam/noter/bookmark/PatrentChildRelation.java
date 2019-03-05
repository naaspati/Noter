package sam.noter.bookmark;

import sam.noter.dao.Entry;
import sam.noter.dao.api.IEntry;

class PatrentChildRelation {
	final IEntry parent, child;
	int index;
	
	PatrentChildRelation(Entry child) {
		this.parent = child.parent();
		this.child = child;
	}
	public void setIndex(int index) {
		this.index = index;
	} 
}