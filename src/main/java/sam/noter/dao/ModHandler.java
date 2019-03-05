package sam.noter.dao;

import java.util.BitSet;

public class ModHandler {
	private static final int TITLE = 0;
	private static final int CONTENT = 1;
	private static final int CHILDREN = 2;
	
	private static final int size = 3;

	private BitSet mod = new BitSet();
	private int add;

	public ModHandler(int minimumValue) {
		this.add = 0 - minimumValue;
	}

	private int index(int id) {
		return (id + add) * size;
	}
	private void check(ModifiedField field) {
		if(field == null)
			throw new NullPointerException();
	}

	public boolean isModified(int id, ModifiedField field) {
		check(field);
		
		id = index(id);
		
		switch (field) {
			case CONTENT:  return mod.get(id + CONTENT);
			case ALL:      return mod.get(id + TITLE) && mod.get(id + CONTENT) && mod.get(id + CHILDREN);
			case ANY:      return mod.get(id + TITLE) || mod.get(id + CONTENT) || mod.get(id + CHILDREN);
			case CHILDREN: return mod.get(id + CHILDREN);
			case TITLE:    return mod.get(id + TITLE);
			default:       throw new IllegalArgumentException();
		}
	}

	public void setModified(int id, ModifiedField field, boolean value) {
		check(field);
		
		id = index(id);
		
		switch (field) {
			case CONTENT: 
				mod.set(id + CONTENT, value);
				break;
			case ALL:
			case ANY:
				mod.set(id + TITLE, value);
				mod.set(id + CONTENT, value);
				mod.set(id + CHILDREN, value);
				break;
			case CHILDREN: 
				mod.set(id + CHILDREN, value);
				break;
			case TITLE:
				mod.set(id + TITLE, value);
				break;
			default:     
				throw new IllegalArgumentException();
		}	
	}

	public void clear() {
		mod.clear();
	}
	public boolean isEmpty() {
		return mod.isEmpty();
	}
	

}
