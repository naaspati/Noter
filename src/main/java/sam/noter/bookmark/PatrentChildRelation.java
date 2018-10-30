package sam.noter.bookmark;

import sam.noter.datamaneger.EntryXML;

class PatrentChildRelation {
	final EntryXML parent, child;
	final int index;
	PatrentChildRelation(EntryXML child) {
		this.parent = (EntryXML) child.getParent();
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