package sam.noter.bookmark;

import sam.myutils.MyUtilsCheck;
import sam.noter.dao.Entry;
import sam.noter.tabs.Tab;
import sam.string.TextSearch;

class TitleSearch extends TextSearch<Entry> {
	
	public TitleSearch() {
		super(w -> w.getTitle().toLowerCase(), 300);
	}
	public void start(Tab tab){
		setAllData(tab.getEntriesMap().values());
	}
	@Override
	public void search(String str) {
		super.search(MyUtilsCheck.isEmpty(str) ? str : str.toLowerCase());
	}
	@Override
	public void stop() {
		// super.stop();
		setOnChange(null);
	}
	public void completeStop(){
		super.stop();
	}
}
