package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;

import sam.collection.IndexGetterIterator;
import sam.functions.IOExceptionConsumer;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.infile.DataMeta;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;
import sam.nopkg.Resources;
import sam.noter.dao.zip.RootEntryZ.EZ;

class IndexHelper {
	private static final int BYTES =  
			Integer.BYTES * 3 + // id + parent_id + order
			Long.BYTES + // last_modified
			DataMeta.BYTES ;

	private static final DataMeta EMPTY = new DataMeta(0, 0);

	static void writeIndex(Path path, ArrayWrap<EZ> entries) throws IOException {
		try(FileChannel fc = FileChannel.open(path, WRITE, TRUNCATE_EXISTING, CREATE);
				Resources r = Resources.get();) {
			
			ByteBuffer buffer = r.buffer();
			buffer.putInt(entries.size());

			if(entries.size() == 0) {
				IOUtils.write(buffer, fc, true);
				return;
			}
			
			int[] orders = new int[entries.size() + 1];
			
			for (int i = 0; i < entries.size(); i++) {
				EZ t = entries.get(i);

				if(buffer.remaining() < BYTES)
					IOUtils.write(buffer, fc, true);

				if(t == null) {
					buffer
					.putInt(-10)
					.putInt(-10)
					.putInt(-10)
					.putLong(-10);
				} else {
					int parent_id = t.getParent().getId();
					int order = orders[parent_id + 1]++;
					
					buffer
					.putInt(t.getId())
					.putInt(t.getParent().getId())
					.putInt(order)
					.putLong(t.getLastModified());	
				}

				DataMeta d = t == null || t.getMeta() == null ? EMPTY : t.getMeta();

				buffer
				.putLong(d.position)
				.putInt(d.size);
			}
			
			IOUtils.write(buffer, fc, true);
			Iterator<String> itr = new IndexGetterIterator<String>(entries.size()) {
				@Override
				public String at(int index) {
					EZ e = entries.get(index);
					return e == null ? "" : e.getTitle();
				}
			};
			StringIOUtils.writeJoining(itr, "\n", BufferConsumer.of(fc, false), buffer, r.chars(), r.encoder());
		}
	}

	static void readIndex(Path path, final ArrayList<TempEntry> entries) throws IOException {
		try(FileChannel fc = FileChannel.open(path, READ);
				Resources r = Resources.get();) {
			ByteBuffer buffer = r.buffer();
			IOUtils.read(buffer, true, fc);

			final int count = buffer.getInt();
			if(count <= 0)
				return;
			
			entries.ensureCapacity(count + 10);

			for (int i = 0; i < count; i++) {
				if(buffer.remaining() < BYTES) {
					IOUtils.compactOrClear(buffer);
					IOUtils.read(buffer, false, fc);
				}

				int id = buffer.getInt();
				int parent_id = buffer.getInt();
				int order = buffer.getInt();
				long lastmodified = buffer.getLong();

				long pos = buffer.getLong();
				int size = buffer.getInt();

				if(id < 0) {
					entries.add(null);
				} else {
					TempEntry t = new TempEntry(id, parent_id, order, lastmodified);
					t.setMeta(new DataMeta(pos, size));
					entries.add(t);	
				} 
			}

			IOExceptionConsumer<StringBuilder> eater = new IOExceptionConsumer<StringBuilder>() {
				int n = 0;
				@Override
				public void accept(StringBuilder sb) {
					if(sb.length() == 0)
						n++;
					else 
						entries.get(n++).setTitle(sb.toString());
				}
			};

			IOUtils.compactOrClear(buffer);
			StringIOUtils.collect0(BufferSupplier.of(fc, buffer), '\n', eater, r.decoder(), r.chars(), r.sb());
			
			Checker.assertTrue(entries.size() == count);
		}
	}
}
