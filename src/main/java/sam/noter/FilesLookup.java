package sam.noter;

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
import java.util.logging.Logger;


import sam.config.Session;
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.logging.MyLoggerFactory;
import sam.myutils.System2;


public class FilesLookup {
	private final Logger LOGGER = MyLoggerFactory.logger(FilesLookup.class);

	// List<Pair<String, Path>> allFiles;
	// String defaultDir;

	public List<File> parse(List<String> args) throws IOException {
		if(args.isEmpty()) 
			return Collections.emptyList();
		
		if(args.size() == 1) {
			File p = find(args.get(0));
			if(p != null) 
				return Collections.singletonList(p);
		}
		
		List<File> files = new ArrayList<>();
		
		for (String s : args) {
			File f = find(s);
			if(f == null)
				LOGGER.severe("file not found for: "+s);
			else
				files.add(f);
		}
		return files;
	}

	private Map<String, Path> files;
	private String[] opencache ;
	private final Path openCacheDir = Utils.APP_DATA.resolve("open_cache");
	
	private File find(final String string) throws UnsupportedEncodingException, IOException {
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
				File f2 = f;
				LOGGER.fine(() -> "loaded from open_cache: "+f2);
				return f;
			}
		}
		
		f = new File(string);
		if(f.exists()) {
			LOGGER.fine(() -> "open as file found: "+string);
			return f;
		};
		
		if(files == null) {
			Path dd = defaultDir();
			if(Files.notExists(dd)) {
				files = Collections.emptyMap();
				LOGGER.severe("default dir, not found: "+dd);
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
		Path p2 = p;
		LOGGER.fine(() -> "find from defaultDir walk, "+string+", path: "+p2);
		save(string, p);
		return p.toFile();
	}

	private String substring(String s) {
		return s.substring(0, s.length() - 6);
	}

	private Path dd;
	private Path defaultDir() {
		if(dd != null) return dd;
		String s = System2.lookup("default.save.dir");
		if(s == null)
			s = Optional.ofNullable(Session.getProperty(getClass(), "default.save.dir")).orElse(System.getenv("USERPROFILE"));
		return dd = Paths.get(s);
	}

	private void save(String key, Path path) throws IOException {
		Path p = openCacheDir.resolve( key);
		Files.createDirectories(p.getParent());
		StringWriter2.setText(p, path.toString());
	}
}