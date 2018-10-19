package sam.apps.jbook_reader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import javafx.util.Pair;


public class CmdLoader {
	private static volatile CmdLoader INSTANCE;

	public static CmdLoader getInstance() {
		return INSTANCE;
	}

	public static void init(String[] args) throws CmdLineException, IOException {
		INSTANCE = new CmdLoader(args);
	}
	public static void clear() {
		INSTANCE = null;
	}

	@Option(name="--help", aliases="-h", usage="print this")
	boolean help;
	@Option(name="--version", aliases="-v", usage="print version")
	boolean version;

	@Option(name = "--open", aliases="-o", usage="open file with matching names / or file if exists")
	List<String> open;

	List<Path> files;

	List<Pair<String, Path>> allFiles;
	String defaultDir;

	public CmdLoader(String[] args) throws CmdLineException, IOException {
		if(Arrays.stream(args).noneMatch(s -> s.charAt(0) == '-')) {
			String[] s = new String[args.length + 1];
			s[0] = "-o";

			System.arraycopy(args, 0, s, 1, args.length);
			args = s;
		}

		CmdLineParser cmd = new CmdLineParser(this);
		cmd.parseArgument(args);

		if(help) {
			cmd.printUsage(System.out);
			System.exit(0);
		}
		if(version) {
			System.out.println("1.22");
			System.exit(0);
		}

		if(open == null || open.isEmpty())
			return;

		Properties config = App.getConfig();
		defaultDir = Optional.ofNullable(config.getProperty("default.save.dir")).orElse(System.getenv("USERPROFILE"));

		files = new ArrayList<>();
		Path searchCacheDir = Paths.get("search_cache");

		for (String s : open) {
			s = s.trim();
			if(s.isEmpty())
				continue;

			File f = new File(s);

			if(f.exists() || (f = new File(defaultDir, s)).exists() ||  (f = new File(defaultDir, s+".jbook")).exists()) {
				files.add(f.toPath());
				continue;
			}

			if(s.contains("/") || s.contains("\\")) {
				System.out.println("no match found for:  "+s);
				continue;
			}

			Path p = searchCacheDir.resolve(s);
			if(Files.exists(p)) {
				p = Paths.get(new String(Files.readAllBytes(p), "utf-8"));
				if(Files.exists(p)) {
					files.add(p);
					continue;
				}
			}

			p = null;

			for (Pair<String,Path> pair : allFiles()) {
				if(s.equalsIgnoreCase(pair.getKey())){
					p = pair.getValue();
					break;
				}
			}

			if(p != null) {
				files.add(p);
				save(s, p, searchCacheDir);
				continue;
			}

			s = s.toLowerCase();
			int c = 0;

			for (Pair<String,Path> pair : allFiles) {
				if(pair.getKey().contains(s)) {
					files.add(p = pair.getValue());
					c++;
				}
			}
			if(c == 0) {
				System.out.println("no match found for:  "+s);
				continue;
			}
			if(c == 1) {
				save(s, p, searchCacheDir);
				continue;
			}
		}
		allFiles = null;
	}

	private void save(String key, Path path, Path searchCacheDir) throws IOException {
		Files.createDirectories(searchCacheDir);
		Files.write(searchCacheDir.resolve(key), path.toString().getBytes("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	private List<Pair<String, Path>> allFiles() {
		if(allFiles == null) {
			try {
				allFiles = Files.walk(Paths.get(defaultDir))
						.filter(p -> p.getFileName().toString().endsWith(".jbook") && Files.isRegularFile(p))
						.map(p -> new Pair<>(p.getFileName().toString().replaceFirst("(?i)\\.jbook$", "").toLowerCase(), p))
						.collect(Collectors.toList());
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return allFiles;
	}
}