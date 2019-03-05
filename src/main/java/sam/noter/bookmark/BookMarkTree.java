package sam.noter.bookmark;

import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import sam.noter.EntryTreeItem;
import sam.noter.dao.api.IEntry;

class BookMarkTree extends TreeView<String> {
	private final MultipleSelectionModel<TreeItem<String>> model;
	
	public BookMarkTree() {
		super();

		model = getSelectionModel();
	}

	public void set(IEntry root) {
		// TODO Auto-generated method stub
		
	}

	public EntryTreeItem itemFor(IEntry item) {
		// TODO Auto-generated method stub
		return null;
	}

	public IEntry getEntry() {
		// TODO Auto-generated method stub
		return null;
	}
	public EntryTreeItem getSelectedItem() {
		return (EntryTreeItem) model.getSelectedItem();
	}

	public void addChild(String title) {
		// TODO Auto-generated method stub
		
	}

	public EntryTreeItem addChild(String title, TreeItem<String> parent, EntryTreeItem item) {
		// TODO Auto-generated method stub
		return null;
	}

	public EntryTreeItem addChild(String title, EntryTreeItem item) {
		// TODO Auto-generated method stub
		return null;
	}

	public EntryTreeItem addChild(String title, TreeItem<String> parent, TreeItem<String> parent2) {
		// TODO Auto-generated method stub
		return null;
	}

	public void clearAndSelect(TreeItem<String> e) {
		model.clearSelection();
		model.select(e);
	}
}
