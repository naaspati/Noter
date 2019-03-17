package sam.noter.dao.zip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.thedeanda.lorem.LoremIpsum;

import sam.io.infile.DataMeta;

class IndexHelperTest {

	@Test
	void test() throws IOException {
		Path p = Files.createTempFile("IndexHelperTest", null);
		Random r = new Random();
		LoremIpsum lorem = new LoremIpsum();
		List<TempEntry> list = new ArrayList<>();
		
		System.out.println(p);
		
		try {
			for (int i = 0; i < 1000; i+=50) {
				test(p, i, r, lorem, list);
			}
			for (int i = 0; i < 20; i++) {
				test(p, r.nextInt(10000), r, lorem, list);
			}
		} finally {
			Files.deleteIfExists(p);
		}
	}

	private void test(Path p, int size, Random r, LoremIpsum lorem, List<TempEntry> list) throws IOException {
		System.out.println("size: "+size);
		
		while(list.size() < size) {
			if(r.nextInt()%11 == 0)
				list.add(null);
			else {
				TempEntry t = new TempEntry(Math.abs(r.nextInt()), r.nextInt(), r.nextInt(), r.nextLong(), lorem.getWords(2, 10));
				t.setMeta(new DataMeta(r.nextLong(), r.nextInt()));
				list.add(t);	
			}
		}
		
		System.out.println("null count: "+list.stream().filter(f -> f == null).count());
		Collections.shuffle(list, r);
		
		IndexHelper.writeIndex(p, list);
		List<TempEntry> actual = IndexHelper.readIndex(p);
		
		assertEquals(list.size(), actual.size());
		
		for (int i = 0; i < list.size(); i++) {
			TempEntry e = list.get(i);
			TempEntry a = actual.get(i);
			
			if(e == null)
				assertNull(a);
			else {
				assertNotNull(a);
				
				assertNotSame(e, a);
				assertEquals(e.id, a.id);
				assertEquals(e.parent_id, a.parent_id);
				assertEquals(e.order, a.order);
				assertEquals(e.lastmodified, a.lastmodified);
				assertEquals(e.title(), a.title());
				assertEquals(e.meta(), a.meta());
			}
		}
	}

}
