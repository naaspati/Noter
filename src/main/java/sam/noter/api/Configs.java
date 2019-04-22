package sam.noter.api;

import java.nio.file.Path;
import java.util.function.Function;

import sam.myutils.System2;

public interface Configs {
    Path tempDir();
    Path appDataDir();
    Path backupDir();

    String getString(String key);
    default String getString(String key, String defaultValue) {
        String s = getString(key);
        return s == null ? defaultValue : s;
    }
    default <E> E get(String key, E defaultValue, Function<String, E> mapper) {
        String s = getString(key);
        if(s == null)
            return defaultValue;

        return mapper.apply(s);
    }

    void setString(String key, String value);
    default boolean getBoolean(String key) {
        return getBoolean(key, false);
    }
    default boolean getBoolean(String key, boolean defaultValue) {
        return System2.parseBoolean(getString(key), defaultValue);
    }
    default int getInt(String key, int defaultValue) {
        String s = getString(key);
        if(s == null)
            return defaultValue;

        return Integer.parseInt(s.trim());
    }
    default double getDouble(String key, double defaultValue) {
        String s = getString(key);
        if(s == null)
            return defaultValue;

        return Double.parseDouble(s.trim());
    }
}
