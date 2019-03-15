package sam.noter.dao.zip;

import java.io.IOException;

import org.slf4j.Logger;

import sam.noter.Utils;
import sam.noter.dao.Entry;

abstract class EntryZ extends Entry {
	private static final Logger logger = Utils.logger(EntryZ.class); 

	public EntryZ(int id, String title) {
		super(id, title);
	}
	@Override
	protected Logger logger() {
		return logger;
	}
	
	@Override
	public String getContent() {
		if(this.content == null) {
			try {
				content = readContent();
			} catch (IOException e) {
				logger().error("failed to load content: {}", this, e);
				content = "";
			}
		}
		return content;
	}
	
	protected abstract String readContent() throws IOException;
	
}
