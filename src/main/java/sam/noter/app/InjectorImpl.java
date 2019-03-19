package sam.noter.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.codejargon.feather.Feather;
import org.codejargon.feather.Key;
import org.codejargon.feather.Provides;

import sam.di.AppConfig;
import sam.di.ConfigKey;
import sam.di.Injector;
import sam.di.OnExitQueue;
import sam.myutils.MyUtilsPath;
import sam.myutils.System2;

class InjectorImpl implements Injector, OnExitQueue, AppConfig {
	final Path appDataDir;
	final Path backupDir; 
	List<Runnable> onExit;

	private final Feather feather;
	private int configMod;
	private EnumMap<ConfigKey, String> configs = new EnumMap<>(ConfigKey.class);
	private final FileChooserHelper fch;
	private final Map<Class, Class> bindings;

	public InjectorImpl(FileChooserHelper fch) throws IOException, ClassNotFoundException {
		HashMap<Class, Class> map = new HashMap<>();

		try(BufferedReader reader = new BufferedReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream("di.bindings"), "utf-8"))) {
			String line = null;
			while((line = reader.readLine()) != null) {
				String s = line.trim();
				
				if(s.isEmpty() || s.charAt(0) == '#')
					continue;
					
				String str[] = s.split("\\s+");

				if(str.length != 2)
					throw new IllegalArgumentException("bad line in di.bindings: "+line);

				map.put(Class.forName(str[0].trim()), Class.forName(str[1].trim()));
			}

			this.bindings = map.isEmpty() ? Collections.emptyMap() : map;
		}
		this.appDataDir = (Path) Objects.requireNonNull(System.getProperties().get("app_data"));
		this.backupDir = appDataDir.resolve("backupDir");
		Files.createDirectories(backupDir);

		this.fch = fch;
		this.feather = Feather.with(this);
	}

	@Override public Path tempDir() { return MyUtilsPath.TEMP_DIR; }
	// change name to appDataDir()
	@Override public Path appDir() { return appDataDir; }
	@Override public Path backupDir() { return backupDir; }

	@Override
	public String getConfig(ConfigKey key) {
		return configs.get(key);
	}
	@Override
	public boolean getConfigBoolean(ConfigKey key) {
		return System2.parseBoolean(getConfig(key), false);
	}
	@Override
	public void setConfig(ConfigKey key, String value) {
		Objects.requireNonNull(key);
		if(!Objects.equals(value, configs.get(key))) {
			configs.put(key, value);
			configMod++;
		}
	}

	@Override
	public <E, A extends Annotation> E instance(Class<E> type, Class<A> qualifier) {
		return feather.instance(Key.of(cls(type), qualifier));
	}
	
	@SuppressWarnings("unchecked")
	private <E> Class<E> cls(Class<E> type) {
		return bindings.getOrDefault(type, type);
	}

	@Override
	public <E> E instance(Class<E> type) {
		return feather.instance(cls(type));
	}

	@Provides public Injector injector( ) { return this; }
	@Provides public OnExitQueue onexit( ) { return this; }
	@Provides public AppConfig cm( ) { return this; }
	@Provides public FileChooserHelper utils( ) { return fch; }

	@Override
	public void runOnExist(Runnable runnable) {
		if(onExit == null)
			onExit = Collections.synchronizedList(new ArrayList<>());
		onExit.add(runnable);
	}
};
