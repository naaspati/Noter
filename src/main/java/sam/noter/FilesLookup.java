package sam.noter;

import static sam.noter.EnvKeys.DEFAULT_SAVE_DIR;
import static sam.noter.EnvKeys.ENABLE_FILE_LOOKUP_OPEN_CACHE;
import static sam.noter.Utils.APP_DATA;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.Logger;

import org.apache.logging.log4j.LogManager;

import sam.config.Session;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.myutils.System2;

public class FilesLookup {
	private final Logger logger = LogManager.getLogger(FilesLookup.class);

	public List<Path> parse(List<String> args) throws IOException {
		if(args.isEmpty()) 
			return Collections.emptyList();

		if(args.size() == 1) {
			File p = find(args.get(0));
			if(p != null) 
				return new ArrayList<>(Arrays.asList(p.toPath()));
		}

		List<Path> files = new ArrayList<>();

		for (String s : args) {
			if(s.trim().isEmpty()) continue;

			File f = find(s);
			if(f == null)
				logger.fatal("file not found for: {}", s);
			else
				files.add(f.toPath());
		}
		return files;
	}

	private Map<String, Path> files;
	private String[] opencache ;
	private final Path openCacheDir = APP_DATA.resolve("open_cache");

	private File find(final String string) throws UnsupportedEncodingException, IOException {
		File f = ENABLE_FILE_LOOKUP_OPEN_CACHE ? openCacheLookup(string) : null;

		if(f != null) return f;

		f = new File(string);
		if(f.exists()) {
			logger.info("FILE_FOUND: {}", string);
			return f;
		};


		if(files == null) {
			Path dd = defaultDir();
			if(Checker.notExists(dd)) {
				files = Collections.emptyMap();
				logger.fatal("default dir, not found: {}", dd);
			} else {
				files = new HashMap<>();
				Files.walk(dd)
				.forEach(p -> {
					String s = p.getFileName().toString().toLowerCase();
					if(!s.endsWith(".jbook"))
						return;
					if(!Files.isRegularFile(p)) return;

					files.put(substring(s), p);
				});
			}			
		}

		if(files.isEmpty()) return null;

		String s = string.toLowerCase(); 
		Path p = files.get(s.endsWith(".jbook") ? substring(s) : s);
		if(p == null) 
			p = files.entrySet().stream().filter(e -> e.getKey().startsWith(string)).findFirst().map(e -> e.getValue()).orElse(null);

		if(p == null) return null;
		logger.info("DEFAULT_DIR_WALK: {} = \"{}\"", string, p);
		save(string, p);
		return p.toFile();
	}

	private File openCacheLookup(String string) throws IOException {
		File f = null;

		if(opencache == null) {
			opencache = openCacheDir.toFile().list();
			if(opencache == null) 
				opencache = new String[0];
			Arrays.sort(opencache, Comparator.naturalOrder());
		}
		if(Arrays.binarySearch(opencache, string) >= 0) {
			f = new File(StringReader2.getText(openCacheDir.resolve( string)));
			if(f.exists()) {
				logger.info("OPEN_CACHE: {} = {}", string, f);
				return f;
			}
		}
		return null;
	}

	private String substring(String s) {
		return s.substring(0, s.length() - 6);
	}

	private Path defaultDir() {
		String s = System2.lookup(DEFAULT_SAVE_DIR);
		if(s == null)
			return Optional.ofNullable(Session.global().getProperty(EnvKeys.DEFAULT_SAVE_DIR)).map(Paths::get).orElse(null);
		else 
			return Paths.get(s);
	}

	private void save(String key, Path path) throws IOException {
		if(!ENABLE_FILE_LOOKUP_OPEN_CACHE) return;

		Path p = openCacheDir.resolve( key);
		Files.createDirectories(p.getParent());
		StringWriter2.setText(p, path.toString());
	}
}