package sam.noter.dao.zip;

import java.io.IOException;
import java.lang.ref.WeakReference;

import org.slf4j.Logger;

import sam.noter.Utils;
import sam.noter.dao.Entry;
import sam.reference.ReferenceUtils;

abstract class EntryZ extends Entry {
	private static final Logger logger = Utils.logger(EntryZ.class);
	private WeakReference<String> _content;

	public EntryZ(int id, String title) {
		super(id, title);
	}
	@Override
	protected Logger logger() {
		return logger;
	}
	
	@Override
	public String getContent() {
		if(this.content != null)
			return content;
		
		String s = ReferenceUtils.get(_content);
		if(s != null)
			return s;
		
		try {
			this._content = new WeakReference<String>(s = readContent());
			logger.debug("load content: length:{}, entry: {}", s.length(), this);
			return s;
		} catch (IOException e) {
			logger.error("failed to load content: {}", this, e);
			this._content = null;
			content = "";
			return content;
		}
	}
	
	@Override
	public void setContent(String content) {
		super.setContent(content == null ? "" : content);
		this._content = null; 
	}
	
	protected abstract String readContent() throws IOException;
	
}
