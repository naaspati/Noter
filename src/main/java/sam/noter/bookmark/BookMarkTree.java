package sam.noter.bookmark;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import sam.myutils.Checker;
import sam.noter.EntryTreeItem;
import sam.noter.Utils;
import sam.noter.dao.api.IEntry;
import sam.noter.dao.api.IRootEntry;
import sam.reference.ReferenceUtils;

class BookMarkTree extends TreeView<String> {
	private static final Logger logger = Utils.logger(BookMarkTree.class);

	private final MultipleSelectionModel<TreeItem<String>> model;
	private final ETM root = new ETM();
	private IRootEntry rootEntry;
	private final ArrayList<WeakReference<ETM>> cache = new ArrayList<>();

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
		if(item == null)
			return null;

		return itemFor(item, root.getChildren());
	}

	private EntryTreeItem itemFor(IEntry item, ObservableList<TreeItem<String>> children) {
		if(Checker.isEmpty(children))
			return null;

		for (int i = 0; i < children.size(); i++) {
			if(children.get(i) == item)
				return (EntryTreeItem) children.get(i);
		}

		for (int i = 0; i < children.size(); i++) {
			TreeItem<String> t = itemFor(item, children.get(i).getChildren()); 
			if(t != null)
				return (EntryTreeItem) t;
		}

		return null;

	}

	public EntryTreeItem getSelectedItem() {
		return (EntryTreeItem) model.getSelectedItem();
	}
	public EntryTreeItem addChild(String title, EntryTreeItem parent, int index) {
		ETM p = (ETM) parent;
		IEntry pie = p.entry;
		IEntry e = rootEntry.addChild(title, pie, index);
		ETM child = newETM();
		child.setEntry(e);

		if(index >= p.list.size())
			p.list.add(child);
		else 
			p.list.add(index <= 0 ? 0 : index, child);

		return child;
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

	private int createdCount = 0; 
	void setRootEntry(IRootEntry root) {
		setRoot(null);

		createdCount = 0;
		set(root.getChildren(), this.root);
		setRoot(this.root);

		cache.forEach(e -> etm(e).setEntry(null));
		logger.debug("created EntryTreeItem: {}", createdCount);
	}
	private void set(Collection<? extends IEntry> children, TreeItem<String> target) {
		ObservableList<TreeItem<String>> items = children(target);

		if(Checker.isEmpty(children)) {
			if(!items.isEmpty()) {
				items.forEach(e -> addToCache(e));
				items.clear();
			}
		} else {
			while(items.size() < children.size())
				items.add(newETM());
			while(items.size() > children.size()) {
				TreeItem<String> item = items.remove(items.size() - 1); 
				remove(item);
				addToCache(item);
			}

			Iterator<? extends IEntry> entries = children.iterator();

			for (int i = 0; i < children.size(); i++) {
				ETM item = etm(items.get(i));
				IEntry child = entries.next();
				item.setEntry(child);

				set(child.getChildren(), item);
			}
		}
	}

	private ETM newETM() {
		while(!cache.isEmpty()) {
			ETM e = ReferenceUtils.get(cache.remove(cache.size() - 1));
			if(e != null)
				return e;
		}
		return new ETM();
	}

	private void addToCache(Collection<TreeItem<String>> e) {
		if(Checker.isNotEmpty(e))
			e.forEach(this::addToCache);
	}
	private void addToCache(TreeItem<String> e) {
		ETM f = (ETM)e;
		f.setEntry(null);
		cache.add(new WeakReference<>(f));
	}

	private void remove(TreeItem<String> item) {
		List<TreeItem<String>> list = children(item);
		if(list.isEmpty())
			return;

		addToCache(list);
		list.forEach(this::remove);
		list.clear();
	}

	private ObservableList<TreeItem<String>> children(TreeItem<String> item) {
		return etm(item).list;
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

	static ETM etm(Object o) {
		return (ETM)o;  
	}

	private class ETM extends EntryTreeItem {
		private IEntry entry;
		private final ObservableList<TreeItem<String>> list;
		private final ObservableList<TreeItem<String>> unmod;

		public ETM() {
			this.list = super.getChildren();
			this.unmod = FXCollections.unmodifiableObservableList(list);
		}
		void setEntry(IEntry e) {
			setValue(e == null ? null : e.getTitle());
			this.entry = e;
		}

		@Override
		public void setContentProxy(Supplier<String> proxy) {

		}
		@Override
		public ObservableList<TreeItem<String>> getChildren() {
			return unmod;
		}
		@Override
		protected IEntry entry() {
			return entry;
		}

		@Override
		public boolean isEmpty() {
			return list.isEmpty();
		}
		@Override
		public int indexOf(EntryTreeItem item) {
			return list.indexOf(item);
		}
	}
}
