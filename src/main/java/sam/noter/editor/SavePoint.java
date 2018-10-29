package sam.noter.editor;

class SavePoint {
	final ViewType type;
	Object data;
	
	public SavePoint(ViewType type) {
		this.type = type;
	}
}
