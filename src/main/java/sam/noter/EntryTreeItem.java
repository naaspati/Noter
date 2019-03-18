package sam.noter;

import static sam.noter.dao.VisitResult.CONTINUE;
import static sam.noter.dao.VisitResult.SKIP_SIBLINGS;
import static sam.noter.dao.VisitResult.TERMINATE;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.scene.control.TreeItem;
import sam.noter.dao.VisitResult;
import sam.noter.dao.Walker;
import sam.noter.dao.api.IEntry;

public abstract class EntryTreeItem extends TreeItem<String> {
	public static EntryTreeItem cast(Object o) {
		return (EntryTreeItem) o;
	}
	
	public abstract void setContentProxy(Supplier<String> proxy);
	protected abstract IEntry entry();
	public abstract boolean isEmpty();
	public abstract int indexOf(EntryTreeItem item);
	
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
	public String getTitle() {
		return entry().getTitle();
	}
	public String getContent() {
		return entry().getContent();
	}
	public void setTitle(String title) {
		entry().setTitle(title);
	}
	public IEntry getEntry() {
		return entry();
	}
	public void setContent(String content) {
		entry().setContent(content);
	}
	
	public long getLastModified() {
		return entry().getLastModified();
	}
	public int getId() {
		return entry().getId();
	}
}
