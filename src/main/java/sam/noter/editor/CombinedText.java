package sam.noter.editor;

import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import sam.noter.datamaneger.EntryXML;
import sam.reference.WeakAndLazy;

class CombinedText extends TextArea {

	private final WeakAndLazy<StringBuilder> buffer = new WeakAndLazy<>(StringBuilder::new);
	private EntryXML item;

	public CombinedText() {
		setEditable(false);
	}

	private String combineChildrenContent(EntryXML item) {
		StringBuilder sb = buffer.get();
		sb.setLength(0);

		combine(sb, item);

		for (TreeItem<String> e : item.getChildren()) 
			combine(sb, (EntryXML)e);

		return sb.toString();
	}

	private StringBuilder combine(StringBuilder sb, EntryXML u) {
		append(sb, '#', u.getTitle().length() + 10);
		sb.append('\n')
		.append("     ").append(u.getTitle()).append('\n');
		append(sb, '#', u.getTitle().length() + 10);
		sb.append('\n').append('\n')
		.append(u.getContent()).append('\n').append('\n');

		return sb;
	}

	private void append(StringBuilder sb, char c, int count) {
		for (int i = 0; i < count; i++) 
			sb.append(c);
	}

	public void setItem(EntryXML item) {
		this.item = item;
		setText(combineChildrenContent(item));
	}
	public EntryXML getItem() {
		return item;
	}
}
