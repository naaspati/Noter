import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JOptionPane;

import javafx.application.Application;
import javafx.scene.control.TreeItem;
import sam.noter.App;
import sam.noter.dao.Entry;
import sam.noter.dao.RootEntry;
import sam.noter.dao.dom.RootDOMEntryFactory;
import sam.noter.dao.zip.RootEntryGFactory;

public class Main {

	public static void main( String[] args ) throws Exception {
		try {
			FileChannel c = FileChannel.open(Paths.get("lock.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			c.tryLock();
			c.write(ByteBuffer.wrap(new byte[]{1}));
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, "Only one instanceof program is allowed", "No Two instance allowed", JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		}
		
		
		load(new File("C:\\Users\\Sameer\\Documents\\MEGAsync\\Mega\\jbooks\\C.jbook"), new File("D:\\importents_are_here\\eclipse_workplace\\javafx\\Noter\\dummy.jbook"));
		
		// Application.launch(App.class, args);
	}

	private static void load(File file, File file2) throws Exception {
		RootDOMEntryFactory factory = new RootDOMEntryFactory();
		RootEntryGFactory factory2 = RootEntryGFactory.getInstance();
		
		RootEntry root = factory.load(file);
		RootEntry root2 = factory2.load(file2);
		
		List<Entry> list1 = new ArrayList<>();
		((Entry)root).walk(list1::add);
		
		List<Entry> list2 = new ArrayList<>();
		((Entry)root2).walk(list2::add);
		
		int n = 0;
		
		for (int i = 0; i < list1.size(); i++) {
			if(!Objects.equals(list1.get(i).getContent(), list2.get(i).getContent())) 
				System.out.println(list1.get(i));
		}
		root2.save(file2);
		System.out.println(n);
	}

	private static void convert(File from, File to) throws Exception {
		RootDOMEntryFactory factory = new RootDOMEntryFactory();
		RootEntryGFactory factory2 = RootEntryGFactory.getInstance();
		
		RootEntry root = factory.load(from);
		RootEntry root2 = factory2.convert(root);
		
		root2.save(to);
		System.out.println("DONE");
		
	}
}
