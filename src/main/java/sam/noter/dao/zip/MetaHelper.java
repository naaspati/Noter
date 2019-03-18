package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sam.collection.CollectionUtils;
import sam.collection.MappedIterator;
import sam.functions.IOExceptionConsumer;
import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.serilizers.StringIOUtils;
import sam.nopkg.Resources;

final class MetaHelper {
	private MetaHelper() { }

	private static final Logger logger = LoggerFactory.getLogger(MetaHelper.class);
	private static final int BYTES = Integer.BYTES + Long.BYTES;

	public static void read(ArrayList<Meta> metas, Path path) throws IOException {
		metas.clear();

		try(InputStream _is = Files.newInputStream(path, READ);
				GZIPInputStream gis = new GZIPInputStream(_is);
				Resources r = Resources.get();
				) {
			ByteBuffer buffer = r.buffer();
			int n = IOUtils.read(buffer, gis, true);

			if(n < 4) {
				logger.debug("expected to read atleast 4 bytes but was: {}", n);
				return;
			}

			final int size = buffer.getInt();

			if(size == 0)
				return;

			metas.ensureCapacity(size + 2);
			CollectionUtils.repeat(metas, null, size);

			for (int i = 0; i < size; i++) {
				if(buffer.remaining() < BYTES) {
					IOUtils.compactOrClear(buffer);
					IOUtils.read(buffer, gis, true);
				}
				Meta m = new Meta(buffer.getInt(), buffer.getLong());
				metas.set(i, m);
			}

			IOUtils.compactOrClear(buffer);
			BufferSupplier supplier = BufferSupplier.of(gis, buffer);
			IOExceptionConsumer<String> collector = new IOExceptionConsumer<String>() {
				int k = 0;
				@Override
				public void accept(String t) {
					metas.get(k++).setSource(Paths.get(t));
				}
			};
			StringIOUtils.collect(supplier, '\n', collector, r.decoder(), r.chars(), r.sb());
		}
	}

	public static void write(List<Meta> metas, Path path) throws IOException {
		try(OutputStream _is = Files.newOutputStream(path, WRITE, TRUNCATE_EXISTING, CREATE);
				GZIPOutputStream gis = new GZIPOutputStream(_is);
				Resources r = Resources.get();
				) {
			if(metas.isEmpty()) {
				ByteBuffer buffer = ByteBuffer.allocate(4);
				buffer.putInt(0).flip();

				gis.write(buffer.array());
				return;
			}

			ByteBuffer buffer = r.buffer();
			buffer.putInt(metas.size());

			for (Meta m : metas) {
				if(buffer.remaining() < BYTES)
					IOUtils.write(buffer, gis, true);
				buffer.putInt(m.id).putLong(m.lastModified());
			}

			IOUtils.write(buffer, gis, true);
			StringIOUtils.writeJoining(new MappedIterator<Meta, String>(metas.iterator(), m -> m.source().toString()), "\n", b -> IOUtils.write(b, gis, false), buffer, r.chars(), r.encoder());
		}	
	}
}
