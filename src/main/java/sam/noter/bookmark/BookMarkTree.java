package sam.noter.bookmark;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import sam.myutils.Checker;
import sam.noter.EntryTreeItem;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;
import sam.reference.ReferenceUtils;

class BookMarkTree extends TreeView<String> {
	private static final Logger logger = LogManager.getLogger(BookMarkTree.class);

	private final MultipleSelectionModel<TreeItem<String>> model;
	private final EntryTreeItem root = new EntryTreeItem();
	private IRootEntry rootEntry;

	public BookMarkTree() {
		super();

		model = getSelectionModel();
		addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
			if(e.getClickCount() > 1) {
				e.consume();
				return;
			}
		});
	}

	public EntryTreeItem itemFor(IEntry item) {
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

	public MultipleSelectionModel<TreeItem<String>> model() {
		return model;
	}
	public IRootEntry getRootEntry() {
		return rootEntry;
	}

	private WeakReference<LinkedList<TreeItem<String>>> wcache = new WeakReference<LinkedList<TreeItem<String>>>(null);
	private int createdCount = 0; 
	void setRootEntry(IRootEntry root) {
		setRoot(null);

		createdCount = 0;
		LinkedList<TreeItem<String>> cache = ReferenceUtils.get(wcache);
		if(cache == null)
			wcache = new WeakReference<>(cache = new LinkedList<>());

		set(root.getChildren(), this.root, cache);
		setRoot(this.root);

		cache.forEach(e -> EntryTreeItem.cast(e).setEntry(null));
		logger.debug(() -> "created EntryTreeItem: "+createdCount);
	}
	private void set(Collection<? extends IEntry> children, TreeItem<String> target, LinkedList<TreeItem<String>> cache) {
		ObservableList<TreeItem<String>> items = target.getChildren();

		if(Checker.isEmpty(children)) {
			if(!items.isEmpty()) {
				cache.addAll(items);
				items.clear();	
			}
		} else {
			while(items.size() < children.size())
				items.add(newInstance(cache.removeLast()));
			while(items.size() > children.size()) {
				TreeItem<String> item = items.remove(items.size() - 1); 
				remove(cache, item);
				cache.add(item);
			}

			Iterator<? extends IEntry> entries = children.iterator();
			
			for (int i = 0; i < children.size(); i++) {
				EntryTreeItem item = EntryTreeItem.cast(items.get(i));
				IEntry child = entries.next();
				item.setEntry(child);

				set(child.getChildren(), item, cache);
			}
		}
	}

	private void remove(LinkedList<TreeItem<String>> cache, TreeItem<String> item) {
		List<TreeItem<String>> list = item.getChildren(); 
		if(list.isEmpty())
			return;

		cache.addAll(list);
		list.forEach(e -> remove(cache, e));
		list.clear();
	}

	private TreeItem<String> newInstance(TreeItem<String> e) {
		if(e != null)
			return e;
		createdCount++;
		return new EntryTreeItem();
	}

	void selectById(int id) {
		selectById(id, root.getChildren());
	}

	private boolean selectById(int id, ObservableList<TreeItem<String>> children) {
		if(children.isEmpty())
			return false;

		for (int i = 0; i < children.size(); i++) {
			EntryTreeItem item = EntryTreeItem.cast(children.get(i));

			if(item.getEntry().getId() == id) {
				model.clearSelection();
				model.select(item);
				return true;
			} else {
				if(selectById(id, item.getChildren()))
					return true;
			}
		}

		return false;
	}
}
