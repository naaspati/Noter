package sam.apps.jbook_reader.editor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import sam.apps.jbook_reader.datamaneger.Entry;
import sam.fx.popup.FxPopupShop;
import sam.myutils.MyUtilsCheck;
import sam.myutils.MyUtilsException;
import sam.weak.WeakAndLazy;

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

	private final WeakAndLazy<LineSplitter> ls = new WeakAndLazy<>(() -> MyUtilsException.noError(() -> new LineSplitter()));

	public void splitLine() {
		if(!content.isEditable()) {
			FxPopupShop.showHidePopup("not editable", 1500);
			return;
		}

		if(MyUtilsCheck.isEmptyTrimmed(content.getSelectedText())) 
			FxPopupShop.showHidePopup("no text selected", 1500);
		else
			ls.get().show(content);
	}
}
