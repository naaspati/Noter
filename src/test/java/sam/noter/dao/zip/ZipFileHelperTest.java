package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.READ;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.nopkg.Resources;
import sam.noter.dao.zip.ZipFileHelper.TempEntry;

class ZipFileHelperTest {

	@Test
	void testParseZip() throws IOException {
		ZipFileHelper zip = new ZipFileHelper();
		Path temp = Files.createTempFile("ZipFileHelperTest", null);
		try {
			Path zippath = Paths.get("java_temp/Android.jbook");
			TextInFile content = new TextInFile(temp, true);
			ArrayList<TempEntry> entries = zip.parseZip(zippath, content);
			
			for (int i = 0; i < entries.size(); i++) {
				if(entries.get(i) != null)
					assertEquals(i, entries.get(i).id);
			}
			
			try(InputStream _is = Files.newInputStream(zippath, READ);
					BufferedInputStream _bis = new BufferedInputStream(_is);
					ZipInputStream zis = new ZipInputStream(_bis);
					Resources r = Resources.get()) {
				
				ByteArrayOutputStream bos = new ByteArrayOutputStream(8 * 1024);
				
				byte[] bytes = r.bytes();
				ByteBuffer buffer = r.buffer();
				CharBuffer chars = r.chars();
				StringBuilder sb = r.sb();
				
				ZipEntry z;
				int count = 0;
				while((z = zis.getNextEntry()) != null) {
					bos.reset();
					IOUtils.pipe(zis, bos, bytes);
					
					int id = ZipFileHelper.contentId(z.getName());
					if(id < ZipFileHelper.MAX_ID) {
						TempEntry e = entries.get(id);
						
						sb.setLength(0);
						buffer.clear();
						chars.clear();
						
						content.readText(e.meta(), buffer, chars, r.decoder(), sb);
						
						assertEquals(bos.toString("utf-8"), sb.toString());
						count++;
					}
				}
				System.out.println("count: "+count);
			}
			
			
		} finally {
			Files.deleteIfExists(temp);
		}
	}
	

}
