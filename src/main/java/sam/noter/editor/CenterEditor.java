package sam.noter.editor;

import java.lang.ref.WeakReference;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.IdentityHashMap;
import java.util.function.Consumer;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.TextArea;
import sam.noter.datamaneger.Entry;
import sam.reference.ReferenceUtils;

public class CenterEditor extends UnitEditor implements ChangeListener<String> {
	private class Save {
		private final String title, content;
		private final int anchor, caret;

		public Save(CenterEditor c) {
			this.title = c.title.getText();
			this.content = c.content.getText();
			anchor = c.content.getAnchor();
			caret = c.content.getCaretPosition();
		}
	}
	private IdentityHashMap<Entry, WeakReference<Save>> cache = new IdentityHashMap<>();

	public CenterEditor() {
		super(null);
		setId("center-editor");
	}
	private static final ZoneOffset offset  = ZoneOffset.of("+05:30");
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.MEDIUM);
	private static String time(long t) {
		if(t == 0)
			return "N/A";
		LocalDateTime dt = LocalDateTime.ofEpochSecond(t/1000, 0, offset);
		return dt.format(formatter);
	}
	public  void clear() {
		item = null;
	}
	@Override
	public void setItem(Entry entry) {
		if(this.item == entry) return;

		close();

		if(entry == null){
			this.item = null;
			content.setEditable(false);
			content.clear();
			title.setText(null);
			return;
		}
		Save save = ReferenceUtils.get(cache.get(entry));

		if(save != null){
			this.item = entry;
			title.setText(save.title);
			content.setText(save.content);
			content.selectRange(save.anchor, save.caret);
		} else {
			super.setItem(entry);
			title.setText("Modified: "+time(entry.getLastModified()));	
		}

		entry.setContentProxy(content::getText);
		content.textProperty().addListener(this);
		content.setEditable(true);
	}
	public void close() {
		content.textProperty().removeListener(this);

		if(this.item == null) return;

		item.setContentProxy(null);
		item.setContent(content.getText());
		cache.put(item, new WeakReference<>(new Save(this)));
		content.clear();
	}
	@Override
	public void updateTitle() {
		if(item == null)
			return;
		cache.remove(item);
		super.updateTitle();
		title.setText("Modified: "+time(item.getLastModified()));
	}
	public void consume(Consumer<TextArea> e) {
		e.accept(content);
	}
	@Override
	public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
		item.setContent(newValue);
		content.textProperty().removeListener(this);
	}
}