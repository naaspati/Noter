package sam.noter.bookmark;

import java.util.Collection;
import java.util.function.Function;

import sam.fx.helpers.FxTextSearch;
import sam.noter.dao.Entry;
import sam.reference.WeakMap;

class TitleSearch extends FxTextSearch<Entry> {
	public TitleSearch() {
		super(mapper(), 300, true);
	}
	private static Function<Entry, String> mapper() {
		return new Function<Entry, String>() {
			WeakMap<Integer, String> map = new WeakMap<>();

			@Override
			public String apply(Entry t) {
				String s = map.get(t.id);
				if(s == null)
					map.put(t.id, s = t.getTitle().toLowerCase());
				return s;
			}
		};
	}
	public void start(Collection<Entry> list){
		setAllData(list);
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
