package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import sam.io.BufferSupplier;
import sam.io.IOUtils;
import sam.io.infile.DataMeta;
import sam.io.infile.TextInFile;
import sam.io.serilizers.StringIOUtils;
import sam.myutils.Checker;
import sam.nopkg.Resources;
import sam.noter.dao.zip.RootEntryZFactory.Meta;
import sam.string.StringSplitIterator;

class Cache implements AutoCloseable {
	private final static int PATH_ID_LOC = 0;
	private final static int META_DATAMETA = 1;

	private final DataMeta[] metas = new DataMeta[2];

	private final Map<Path, Meta> meta = new HashMap<>();
	private int metaMod;
	private Path path;
	private final TextInFile index;
	private final TextInFile content;

	public Cache(Path path) throws IOException {
		this.path = path;

		if(Files.exists(path)) {
			try(FileChannel fc = FileChannel.open(path, READ);
					Resources r = Resources.get()) {

				ByteBuffer buffer = r.buffer();
				buffer.limit(metas.length * DataMeta.BYTES);
				fc.read(buffer, 0);

				if(buffer.hasRemaining())
					throw new IOException("bad meta file");

				buffer.flip();
				for (int i = 0; i < metas.length; i++) 
					metas[i] = new DataMeta(buffer.getLong(), buffer.getInt());

				buffer.clear();

				DataMeta d = metas[PATH_ID_LOC]; 
				long pos = d.position;
				int size = d.size;

				if(size != 0) {
					StringBuilder sb = r.sb();
					StringIOUtils.read(BufferSupplier.of(fc, buffer, pos, size), sb);

					Iterator<String> itr = new StringSplitIterator(sb, '\t');
					while (itr.hasNext()) 
						this.meta.put(Paths.get(itr.next()), newMeta(Integer.parseInt(itr.next()), null));

					IOUtils.setFilled(buffer);

					d = metas[META_DATAMETA];
					size = d.size;
					pos = d.position;

					Checker.assertTrue(d.size > 0);

					Map<Integer, DataMeta> map = new HashMap<>();

					while(true) {
						IOUtils.compactOrClear(buffer);
						int n = IOUtils.read(buffer, pos, size, fc);
						size -= n;
						pos  += n;

						while(buffer.remaining() >= Meta.BYTES)
							map.put(buffer.getInt(), new DataMeta(buffer.getLong(), buffer.getInt()));

						if(size <= 0)
							break;
					}

					this.meta.forEach((p, meta) -> {
						setMeta(meta, map.get(meta.id));
						if(getMeta(meta) == null)
							throw new IllegalStateException("no meta found for: "+meta.id+": "+p);
					});
				}
			}
		}
	}

	protected abstract Meta newMeta(int id, DataMeta dm);
	protected abstract DataMeta getMeta(Meta meta);
	protected abstract void setMeta(Meta meta, DataMeta dm);
	
	@Override
	public void close() throws Exception {
		if(metaMod != 0) {
			try(FileChannel fc = FileChannel.open(path, WRITE, CREATE, TRUNCATE_EXISTING);
					Resources r = Resources.get()) {

				long initpos = (metas.length + 1) * DataMeta.BYTES;
				long pos[] = {initpos};

				ByteBuffer buffer = r.buffer();

				StringBuilder sb = r.sb();
				this.meta.forEach((s, t) -> sb.append(s).append('\t').append(t.id).append('\t'));

				StringIOUtils.write(b -> pos[0] += IOUtils.write(b, pos[0], fc, false), sb, r.encoder(), buffer);

				metas[PATH_ID_LOC] = new DataMeta(initpos, (int) (pos[0] - initpos));

				initpos = pos[0];
				buffer.clear();

				for (Meta m : meta.values()) {
					if(buffer.remaining() < Meta.BYTES)
						pos[0] += IOUtils.write(buffer, pos[0], fc, true);

					DataMeta dm = getMeta(m);
					buffer.putInt(m.id)
					.putLong(dm.position)
					.putInt(dm.size);
				}

				pos[0] += IOUtils.write(buffer, pos[0], fc, true);

				metas[META_DATAMETA] = new DataMeta(initpos, (int) (pos[0] - initpos));

				pos[0] = 0;
				buffer.clear();

				for (DataMeta m : metas) {
					if(buffer.remaining() < DataMeta.BYTES)
						pos[0] += IOUtils.write(buffer, pos[0], fc, true);

					buffer
					.putLong(m.position)
					.putInt(m.size);
				}

				pos[0] += IOUtils.write(buffer, pos[0], fc, true);
			}
		}
	}

	public Meta get(Path file) {
		return meta.get(file);
	}
	public Meta put(Path file) {
		metaMod++;
		Meta mt = newMeta(meta.values().stream().mapToInt(m -> m.id).max().getAsInt() + 1, null);
		meta.put(file, mt);

		return mt;
	}

	public ArrayWrap<EntryZ> getEntries(RootEntryZ rootEntryZ) {
		// TODO Auto-generated method stub
		return null;
	}

	public Path source() {
		// TODO Auto-generated method stub
		return null;
	}

	public void save(Path file) {
		// TODO Auto-generated method stub
		
	}

	public String readContent(RootEntryZ root, EntryZ e) throws IOException {
		if(root.meta.isNew)
			return "";
		
		if(e.getId() >= root.meta.contents.length)
			return "";
		
		DataMeta dm = root.meta.contents[e.getId()];
		if(dm == null || dm.size == 0)
			return "";
		try(Resources r = Resources.get()) {
			StringBuilder sb = r.sb();
			content.readText(dm, r.buffer(), r.chars(), r.decoder(), sb);
			return sb.toString();
		}
	}
}