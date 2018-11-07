package sam.noter.bookmark;

import sam.noter.dao.Entry;

@Deprecated
class PatrentChildRelation {
	final Entry parent, child;
	final int index;
	PatrentChildRelation(Entry child) {
		this.parent = (Entry) child.getParent();
		this.child = child;
		this.index = parent.indexOf(child);
	}
	void removeChildFromParent() {
		//FIXME parent.remove(child);
	}
	public void addChildToParent() {
		//FIXME parent.add(index, child);
	}
}