package sam.noter.bookmark;

import sam.noter.datamaneger.Entry;

class PatrentChildRelation {
	final Entry parent, child;
	final int index;
	PatrentChildRelation(Entry child) {
		this.parent = (Entry) child.getParent();
		this.child = child;
		this.index = parent.indexOf(child);
	}
	void removeChildFromParent() {
		parent.remove(child);
	}
	public void addChildToParent() {
		parent.add(index, child);
	}
}