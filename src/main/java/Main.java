import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.swing.JOptionPane;

import javafx.application.Application;
import sam.io.fileutils.FilesUtilsIO;
import sam.myutils.MyUtilsException;
import sam.myutils.MyUtilsPath;
import sam.myutils.System2;
import sam.noter.App;
import sam.noter.Utils;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;
import sam.noter.dao.dom.RootDOMEntryFactory;
import sam.noter.dao.zip.RootEntryZFactory;

public class Main {
	public static void main( String[] args ) throws Exception {
		
		Path appdata = Optional.ofNullable(System2.lookupAny("app_data", "APP_DATA","app.data", "APP.DATA"))
				.map(Paths::get)
				.orElseGet(() -> MyUtilsException.noError(() -> Paths.get(ClassLoader.getSystemResource(".").toURI()).resolve("app_data")));
		
		Path openfiles = appdata.resolve("open_files"); 
		
		try {
			FilesUtilsIO.createFileLock("noter.lock");
		} catch (IOException e) {
			if(args.length == 0) {
				JOptionPane.showMessageDialog(null, "Only one instanceof program is allowed", "No Two instance allowed", JOptionPane.ERROR_MESSAGE);
				System.exit(0);
			}
			Files.write(openfiles.resolve(String.valueOf(System.currentTimeMillis())), Arrays.asList(args), StandardOpenOption.CREATE);
			return;
		}
		
		System.getProperties().put("noter.app.data", appdata);
		System.getProperties().put("noter.open.files", openfiles);
		Files.createDirectories(openfiles);

		Utils.init();
		Application.launch(App.class, args);
	}

	@SuppressWarnings("unused")
	private static void convertFolder() throws IOException {
		Path root = Paths.get("C:\\Users\\Sameer\\Documents\\MEGAsync\\Mega");
		Path jbook = root.resolve("jbooks");
		Path zips = root.resolve("jbooks.zip");

		Files.walk(jbook)
		.filter(Files::isRegularFile)
		.forEach(s -> {
			Path t = zips.resolve(MyUtilsPath.subpath(s, jbook));
			try {
				Files.createDirectories(t.getParent());
				convert(s, t);
				check(s, t);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		System.out.println("DONE");
	} 

	@SuppressWarnings("unused")
	private static void check(Path xml, Path zip) throws Exception {
		RootDOMEntryFactory domF = new RootDOMEntryFactory();
		RootEntryZFactory zF = RootEntryZFactory.getInstance();

		RootEntry  dom = domF.load(xml);
		RootEntry z = zF.load(zip);

		ArrayList<Entry> domList = new ArrayList<>(); 
		((Entry)dom).walk(w -> domList.add(w));

		ArrayList<Entry> zList = new ArrayList<>(); 
		((Entry)z).walk(w -> zList.add(w));

		List<String> out = new ArrayList<>();

		int n = 0;
		for (Entry e : domList) {
			Entry f = zList.get(n++);
			if(!Objects.equals(e.getTitle(), f.getTitle()) || !Objects.equals(e.getContent(), f.getContent()))
				out.add(e+"  "+f+"  title: "+(Objects.equals(e.getTitle(), f.getTitle()) +",  content:"+ Objects.equals(e.getContent(), f.getContent())));//+"  "+e.getContent() +"\n"+f.getContent());
		}

		if(!out.isEmpty()) {
			System.out.println("\n");
			System.out.println(String.join("\n", out));
		}
		System.out.println("DONE");
	}
	@SuppressWarnings("unused")
	private static void convert(Path xml, Path zip) throws Exception {
		RootDOMEntryFactory factory = new RootDOMEntryFactory();
		RootEntryZFactory factory2 = RootEntryZFactory.getInstance();

		RootEntry root = factory.load(xml);
		RootEntry root2 = factory2.convert(root);

		root2.save(zip);
		System.out.println("DONE");
	}
}
