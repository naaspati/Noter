package sam.noter.dao.zip;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.nio.file.StandardOpenOption.*;
import static sam.noter.dao.zip.Utils.ensureIndex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.StandardOpenOption.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.functions.IOExceptionConsumer;
import sam.io.IOUtils;
import sam.io.ReadableByteChannelCustom;
import sam.io.WritableByteChannelCustom;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.io.serilizers.StringIOUtils;
import sam.io.serilizers.WriterImpl;
import sam.myutils.Checker;
import sam.nopkg.Resources;
import sam.noter.dao.api.IEntry;
import sam.reference.WeakAndLazy;
import sam.string.StringSplitIterator;

class ZipExtractor {
	private static final Logger logger = LoggerFactory.getLogger(ZipExtractor.class);

	static final int MAX_ID = 5000;

	private static final String INDEX = "index";
	private static final String CONTENT_PREFIX = "content/";
	private static final String CONTENT_PREFIX_2 = "content\\";


	public void parseZip(Path path, TextInFile content, final ArrayList<TempEntry> entries) throws IOException {
		try(InputStream _is = Files.newInputStream(path, READ);
				ZipInputStream zis = new ZipInputStream(new BufferedInputStream(_is));
				Resources r = Resources.get();) {

		    ByteBuffer buf = r.buffer();
		    ReadableByteChannel readable = ReadableByteChannelCustom.of(zis, buf);
			ArrayList<DataMeta> metas = null;

			ZipEntry z;
			while((z = zis.getNextEntry()) != null) {
				if(INDEX.equals(z.getName())) {
					readIndex(readable, r, entries);
					setMetas(entries, metas);
					metas = null;
				} else {
					int id = contentId(z.getName());

					if(id < MAX_ID) {
					    buf.clear();
						DataMeta dm = content.write(readable);
						
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

			if(entries != null && metas != null)
				setMetas(entries, metas);
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

	private void readIndex(ReadableByteChannel readable, Resources r, final ArrayList<TempEntry> entries) throws IOException {
		StringBuilder sb = r.sb();
		CharBuffer chars = r.chars();
		chars.clear();

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
						iter.next() // title
						);


				if(t.id < 0 || t.id > MAX_ID)
					throw new RuntimeException("bad id:("+t.id+") in line-num: "+line+", \nline: \""+sb+"\"\nentry: "+t);

				ensureIndex(entries, t.id);
				entries.set(t.id, t);
			}
		};
		
		StringIOUtils.collect0(readable, '\n', eater, r.decoder(), chars, sb);
	}

	private static final WeakAndLazy<ByteBuffer> wbuffer = new WeakAndLazy<>(() -> ByteBuffer.allocate(8124));

	public void zip(Path target, RootEntryZ root, TextInFile content) throws IOException {
		Path temp = _zip(target, root, content);

		Files.move(temp, target, REPLACE_EXISTING);
		logger.debug("MOVED: {} -> {}", temp, target);
	} 
	
	private Path _zip(Path target, RootEntryZ root, TextInFile content) throws IOException {
		Path temp = Files.createTempFile(target.getFileName().toString(), null);
		
		//TODO
		
		synchronized (wbuffer) {
		    ByteBuffer btempbuf = wbuffer.get();
		    btempbuf.clear();
		    
		    try(OutputStream _is = WritableByteChannelCustom.newOutputStream(temp, btempbuf, false);
		            ZipOutputStream zos = new ZipOutputStream(_is);
	                Resources r = Resources.get();
	                ) {

	            if(root.childrenCount() == 0)
	                return temp;
	            
	            int[] ids = new int[root.maxId() + 1];
	            root.forEachFlattened(new Consumer<IEntry>() {
	                int n = 0;
	                @Override
	                public void accept(IEntry t) {
	                    ids[t.getId()] = n++;
	                }
	            });
	            
	            final int[] copy = Arrays.copyOf(ids, ids.length);

	            IOExceptionConsumer<String> putEntry = name -> {
	                ZipEntry z = new ZipEntry(name);
	                zos.putNextEntry(z);
	            };

	            putEntry.accept(INDEX);
	            ByteBuffer buffer = r.buffer();
	            
	            try(WriterImpl w = new WriterImpl(BufferConsumer.of(zos, false), buffer, r.chars(), false, r.encoder())) {
	                writeIndex(w, root.getChildren(), -1, ids);
	            }
	            zos.closeEntry();
	            
	            Checker.assertTrue(Arrays.equals(ids, copy));
	            buffer.clear();
	            BufferConsumer consumer = BufferConsumer.of(zos, false);
	            
	            root.forEachFlattened0(e -> {
	                DataMeta d = e.getMeta();
	                if(DataMeta.isEmpty(d))
	                    return;

	                putEntry.accept(CONTENT_PREFIX.concat(ts(ids[e.getId()])));
	                buffer.clear();
	                content.writeTo(d, consumer, buffer);
	                zos.closeEntry();   
	            });
	            
	            Checker.assertTrue(Arrays.equals(ids, copy));
	        }    
        }
		return temp;
	}

    private void writeIndex(WriterImpl w, Collection<? extends IEntry> children, int parent_id, int[] ids) throws IOException {
		if(Checker.isEmpty(children))
			return ;
		
		/* 
		 * id
		 * parent_id
		 * order
		 * lastmodified
		 * title
		 */
		
		int order = 0;
		boolean has = false;
		for (IEntry e : children) {
			w.append(ts(ids[e.getId()])).append(' ')
			.append(ts(parent_id)).append(' ')
			.append(ts(order++)).append(' ')
			.append(Long.toString(e.getLastModified())).append(' ')
			.append(e.getTitle()).append('\n');
			
			has = has || e.childrenCount() != 0;
		}
		
		if(has) {
			for (IEntry e : children) {
				if(e.childrenCount() != 0)
					writeIndex(w, e.getChildren(), ids[e.getId()], ids);
			}
		}
	}

    private String ts(int i) {
        return Utils.toString(i);
    }
}
