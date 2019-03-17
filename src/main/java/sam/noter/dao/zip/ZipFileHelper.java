package sam.noter.dao.zip;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.nio.file.StandardOpenOption.READ;
import static sam.noter.dao.zip.Utils.ensureIndex;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.functions.IOExceptionConsumer;
import sam.io.BufferSupplier;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.io.serilizers.StringIOUtils;
import sam.nopkg.Resources;
import sam.string.StringSplitIterator;

class ZipFileHelper {
	private static final Logger logger = LoggerFactory.getLogger(ZipFileHelper.class);

	static final int MAX_ID = 5000;

	private static final String INDEX = "index";
	private static final String CONTENT_PREFIX = "content/";
	private static final String CONTENT_PREFIX_2 = "content\\";


	public ArrayList<TempEntry> parseZip(Path path, TextInFile content) throws IOException {
		try(InputStream _is = Files.newInputStream(path, READ);
				BufferedInputStream _bis = new BufferedInputStream(_is);
				ZipInputStream zis = new ZipInputStream(_bis);
				Resources r = Resources.get();) {

			ByteBuffer buffer = r.buffer();
			ArrayList<DataMeta> metas = null;
			ArrayList<TempEntry> entries = null;

			ZipEntry z;
			while((z = zis.getNextEntry()) != null) {
				if(INDEX.equals(z.getName())) {
					entries = readIndex(zis, r);
					setMetas(entries, metas);
					metas = null;
				} else {
					int id = contentId(z.getName());

					if(id < MAX_ID) {
						buffer.clear();
						DataMeta dm = content.write(BufferSupplier.of(zis, buffer));
						if(entries != null)
							setMeta(id, dm, entries);
						else {
							if(metas == null)
								metas = new ArrayList<>();
							ensureIndex(metas, id);
							metas.set(id, dm);
						}
					} else {
						logger.warn("unknown entry: {}", z.getName());
					}
				}

				zis.closeEntry();
			}

			if(entries == null)
				return new ArrayList<>();

			if(metas != null)
				setMetas(entries, metas);

			return entries;
		}
	}

	private void setMetas(ArrayList<TempEntry> entries, ArrayList<DataMeta> metas) {
		if(metas == null)
			return;

		for (int i = 0; i < metas.size(); i++) {
			DataMeta d = metas.get(i);
			if(d != null)
				setMeta(i, d, entries);
		}
	}
	private void setMeta(int index, DataMeta d, ArrayList<TempEntry> entries) {
		if(d != null) {
			TempEntry t = entries.size() < index ? null : entries.get(index);
			if(t != null)
				t.setMeta(d);
		}
	}

	static int contentId(String name) {
		try {
			if(name.startsWith(CONTENT_PREFIX))
				return parseInt(name.substring(CONTENT_PREFIX.length()));
			if(name.startsWith(CONTENT_PREFIX_2))
				return parseInt(name.substring(CONTENT_PREFIX_2.length()));
		} catch (Throwable e) {
			logger.warn("bad zip entry name: {}",name, e);
		}
		return Integer.MAX_VALUE;
	}

	private ArrayList<TempEntry> readIndex(InputStream zis, Resources r) throws IOException {
		StringBuilder sb = r.sb();
		ByteBuffer buffer = r.buffer();
		buffer.clear();
		CharBuffer chars = r.chars();
		chars.clear();

		ArrayList<TempEntry> entries = new ArrayList<>();

		IOExceptionConsumer<StringBuilder> eater = new IOExceptionConsumer<StringBuilder>() {
			int line = 0;
			@Override
			public void accept(StringBuilder sb) {
				line++;

				Iterator<String> iter = new StringSplitIterator(sb, ' ', 5);
				TempEntry t = new TempEntry(
						parseInt(iter.next()),   // id, 
						parseInt(iter.next()),   // parent_id, 
						parseInt(iter.next()),   // order, 
						parseLong(iter.next()),    // lastmodified, 
						iter.next());


				if(t.id < 0 || t.id > MAX_ID)
					throw new RuntimeException("bad id:("+t.id+") in line-num: "+line+", \nline: \""+sb+"\"\nentry: "+t);

				ensureIndex(entries, t.id);
				entries.set(t.id, t);
			}
		};

		StringIOUtils.collect0(BufferSupplier.of(zis, buffer), '\n', eater, r.decoder(), chars, sb);
		return entries;
	}
}
