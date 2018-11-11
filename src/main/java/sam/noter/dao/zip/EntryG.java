package sam.noter.dao.zip;

import java.io.IOException;
import java.util.List;

import sam.noter.dao.Entry;
import sam.noter.dao.zip.RootEntryGFactory.CacheDir;

class EntryG extends Entry {
	private final CacheDir dir;
	private boolean contentLoaded;
	
	public EntryG(CacheDir dir, int id, long lastModified, String title) {
		super(id, title, null, lastModified);
		this.dir = dir;
	}

	public EntryG(CacheDir dir, int id, String title, boolean isNew) {
		super(id, title);
		this.dir = dir;
		if(isNew) {
			lastModified = System.currentTimeMillis();
			titleM = true;
			contentM = true;
			childrenM = true;
			contentLoaded = true;
		}
	}
	
	public EntryG(int id, Entry from) {
		super(id, from);
		dir = null;
		contentLoaded = true;
	}

	@Override
	public String getContent() {
		if(!contentLoaded) {
			contentLoaded = true;
			try {
				content = dir.getContent(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return super.getContent();
	}
	
	@Override protected void loadChildren(@SuppressWarnings("rawtypes") List sink) { /*DOES NOTHING */ }

	public void setItems(List<EntryG> items) {
		this.items.setAll(items);
	}
	@Override
	protected void clearModified() {
		super.clearModified();
	}
}
