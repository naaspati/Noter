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
import java.util.List;

import sam.collection.MappedIterator;
import sam.functions.IOExceptionConsumer;
import sam.io.BufferConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.infile.DataMeta;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;
import sam.nopkg.Resources;

class IndexHelper {
	private static final int BYTES =  
			Integer.BYTES * 3 + // id + parent_id + order
			Long.BYTES + // last_modified
			DataMeta.BYTES ;

	private static final DataMeta EMPTY = new DataMeta(0, 0);

	static void writeIndex(Path path, List<TempEntry> entries) throws IOException {
		try(FileChannel fc = FileChannel.open(path, WRITE, TRUNCATE_EXISTING, CREATE);
				Resources r = Resources.get();) {
			ByteBuffer buffer = r.buffer();

			buffer.putInt(entries.size());

			if(entries.isEmpty()) {
				IOUtils.write(buffer, fc, true);
				return;
			}
			for (int i = 0; i < entries.size(); i++) {
				TempEntry t = entries.get(i);

				if(buffer.remaining() < BYTES)
					IOUtils.write(buffer, fc, true);

				if(t == null) {
					buffer
					.putInt(-10)
					.putInt(-10)
					.putInt(-10)
					.putLong(-10);
				} else {
					buffer
					.putInt(t.id)
					.putInt(t.parent_id)
					.putInt(t.order)
					.putLong(t.lastmodified);	
				}

				DataMeta d = t == null || t.meta() == null ? EMPTY : t.meta();

				buffer
				.putLong(d.position)
				.putInt(d.size);
			}
			
			IOUtils.write(buffer, fc, true);
			StringIOUtils.writeJoining(new MappedIterator<>(entries.iterator(), t -> t == null ? "" : t.title()), "\n", BufferConsumer.of(fc, false), buffer, r.chars(), r.encoder());
		}
	}

	static List<TempEntry> readIndex(Path path) throws IOException {
		try(FileChannel fc = FileChannel.open(path, READ);
				Resources r = Resources.get();) {
			ByteBuffer buffer = r.buffer();
			IOUtils.read(buffer, true, fc);

			final int count = buffer.getInt();
			ArrayList<TempEntry> list = new ArrayList<>(count + 10);

			if(count <= 0)
				return list;

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
					list.add(null);
				} else {
					TempEntry t = new TempEntry(id, parent_id, order, lastmodified);
					t.setMeta(new DataMeta(pos, size));
					list.add(t);	
				} 
			}

			IOExceptionConsumer<StringBuilder> eater = new IOExceptionConsumer<StringBuilder>() {
				int n = 0;
				@Override
				public void accept(StringBuilder sb) {
					if(sb.length() == 0)
						n++;
					else 
						list.get(n++).setTitle(sb.toString());
				}
			};

			IOUtils.compactOrClear(buffer);
			StringIOUtils.collect0(BufferSupplier.of(fc, buffer), '\n', eater, r.decoder(), r.chars(), r.sb());
			
			Checker.assertTrue(list.size() == count);
			return list;
		}
	}
}
