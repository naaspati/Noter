package sam.apps.jbook_reader.editor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import sam.apps.jbook_reader.datamaneger.Entry;

public class CenterEditor extends UnitEditor{

	public CenterEditor() {
		super(null);
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
	public void setItem(Entry item) {
		super.setItem(item);
		title.setText("Modified: "+time(item.getLastModified()));
		content.setEditable(true);
	}
	@Override
	public void updateTitle() {
		if(item == null)
			return;
		super.updateTitle();
		title.setText("Modified: "+time(item.getLastModified()));
	}
}
