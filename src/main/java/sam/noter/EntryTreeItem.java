package sam.noter;

import static sam.noter.dao.VisitResult.CONTINUE;
import static sam.noter.dao.VisitResult.SKIP_SIBLINGS;
import static sam.noter.dao.VisitResult.TERMINATE;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import sam.nopkg.Junk;
import sam.noter.dao.VisitResult;
import sam.noter.dao.Walker;
import sam.noter.dao.api.IEntry;

public class EntryTreeItem extends TreeItem<String> {
	private IEntry entry;
	
	public static EntryTreeItem cast(Object o) {
		return (EntryTreeItem) o;
	}
	
	public void walk(Consumer<EntryTreeItem> consumer) {
		walkTree(w -> {
			consumer.accept(w);
			return CONTINUE;
		});
	}
	public void walkTree(Walker<EntryTreeItem> walker) {
		walk0(this, walker);
	}
	private VisitResult walk0(EntryTreeItem parent, Walker<EntryTreeItem> walker) {
		if(parent.getChildren().isEmpty()) 
			return CONTINUE;

		for (TreeItem<String> item : parent.getChildren()) {
			EntryTreeItem e = (EntryTreeItem) item;
			VisitResult v = walker.accept(e);

			switch (v) {
				case CONTINUE:
					v = walk0(e, walker);
					if(v == TERMINATE) return TERMINATE;
					if(v == SKIP_SIBLINGS) return CONTINUE;
					break;

				case SKIP_SIBLINGS: return CONTINUE;
				case SKIP_SUBTREE: break;
				case TERMINATE: return TERMINATE;
			}
		}
		return CONTINUE;
	}
	
	
	@SuppressWarnings("unchecked")
	protected void addAll(@SuppressWarnings("rawtypes") List child, int index) {
		/*
		 * if(index <= 0)
			list.addAll(0, child);
		else if(index >= size())
			list.addAll(child);
		else
			list.addAll(index, child);
		 */
	}
	protected void add(IEntry child, int index) {
		/*
		 * if(index <= 0)
			list.add(0, child);
		else if(index >= size())
			list.add(child);
		else
			list.add(index, child);
		 */
	}
	
	public void setSelectedItem(EntryTreeItem e) {
		//TODO
	}
	public EntryTreeItem getSelectedItem() {
		return Junk.notYetImplemented(); 
	}
	
	public String getTitle() {
		return entry.getTitle();
	}
	public String getContent() {
		return entry.getContent();
	}
	public void setTitle(String title) {
		entry.setTitle(title);
	}
	public IEntry getEntry() {
		return entry;
	}
	public void setEntry(IEntry iEntry) {
		// TODO Auto-generated method stub
		Junk.notYetImplemented();
		//FIXME 
		
	}
	public void setContent(String content) {
		entry.setContent(content);
	}
	public void setContentProxy(Supplier<String> proxy) {
		// TODO Auto-generated method stub
		
	}
	public long getLastModified() {
		return entry.getLastModified();
	}
	public int getId() {
		return entry.getId();
	}
	public boolean isContentLoaded() { // possibly will be removed
		// TODO Auto-generated method stub
		return false;
	}
	public boolean isEmpty() {
		// TODO Auto-generated method stub
		return false;
	}
	public int indexOf(EntryTreeItem item) {
		// TODO Auto-generated method stub
		return 0;
	}
}
