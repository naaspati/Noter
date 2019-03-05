package sam.di;

import java.nio.file.Path;
import java.util.function.Function;

public interface ConfigManager {
	Path appDir();
	Path backupDir();
	String getConfig(ConfigKey key);
	default <E> E getConfig(ConfigKey key, E defaultValue, Function<String, E> mapper) {
		String s = getConfig(key);
		if(s == null)
			return defaultValue;
		
		return mapper.apply(s);
	}
	
	void setConfig(ConfigKey key, String value);
	
}
