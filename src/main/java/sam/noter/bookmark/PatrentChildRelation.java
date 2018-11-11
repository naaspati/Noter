package sam.noter.bookmark;

import sam.noter.dao.Entry;
import sam.noter.tabs.Tab;

class PatrentChildRelation {
	final Tab tab;
	final Entry parent, child;
	private int index;
	
	PatrentChildRelation(Tab tab, Entry child) {
		this.parent = child.parent();
		this.child = child;
		this.tab = tab;
	}
	void removeChildFromParent() {
		this.index = parent.indexOf(child);
		tab.removeFromParent(child);
	}
	public void addChildToParent() {
		tab.addChild(child, parent, index);
	}
}