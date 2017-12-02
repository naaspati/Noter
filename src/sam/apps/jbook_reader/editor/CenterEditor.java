package sam.apps.jbook_reader.editor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import javafx.scene.control.TreeItem;
import sam.apps.jbook_reader.tabs.Tab;

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
		tab = null;
		item = null;
	}
	@Override
	public void set(Tab tab, TreeItem<String> item) {
		super.set(tab, item);
		title.setText("Modified: "+time(tab.getLastModifiedTime(item)));
	}
	@Override
	public void updateTitle() {
		if(tab == null || item == null)
			return;
		super.updateTitle();
		title.setText("Modified: "+time(tab.getLastModifiedTime(item)));
	}
	
}
