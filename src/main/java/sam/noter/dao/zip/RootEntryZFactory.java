package sam.noter.dao.zip;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import sam.io.IOUtils;
import sam.io.ReadableByteChannelCustom;
import sam.io.fileutils.FilesUtilsIO;
import sam.io.serilizers.StringIOUtils;
import sam.io.serilizers.WriterImpl;
import sam.myutils.Checker;
import sam.myutils.ThrowException;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Junk;
import sam.nopkg.Resources;
import sam.noter.Utils;
import sam.noter.api.Configs;
import sam.noter.dao.RootEntryFactory;
import sam.reference.WeakAndLazy;

@Singleton
public class RootEntryZFactory implements RootEntryFactory {
    private static final EnsureSingleton singleton = new EnsureSingleton();
    {singleton.init();}

    static final WeakAndLazy<ArrayList<TempEntry>> wlist = new WeakAndLazy<>(ArrayList::new);
    static final WeakAndLazy<ByteBuffer> wbuffer = new WeakAndLazy<>(() -> ByteBuffer.allocate(8124));
    private static final String WRITE_ALL = new String();

    private final Logger logger = Utils.logger(getClass());
    private final Path mydir;
    private final Path meta_path_path, meta_path_meta; 
    private final ArrayList<MetaImpl> _metas = new ArrayList<>();
    private final List<Path> metas_sorted_paths;
    private final IdentityHashMap<Meta, CachedRoot> active = new IdentityHashMap<>();

    @Inject
    public RootEntryZFactory(Configs config) throws IOException {
        this.mydir = config.tempDir().resolve(getClass().getName());
        this.meta_path_path = mydir.resolve("meta_path_path");
        this.meta_path_meta = mydir.resolve("meta_path_meta");

        if(!readMetas())
            FilesUtilsIO.deleteDir(mydir);

        Files.createDirectories(mydir);

        metas_sorted_paths = _metas.isEmpty() ? new ArrayList<>() : _metas.stream().sorted(Comparator.comparingInt(m -> m.order)).map(MetaImpl::source).collect(Collectors.toList());
    }

    private boolean readMetas() {
        try {
            Path path = meta_path_path;
            Path meta = meta_path_meta;

            if(Files.notExists(meta) || Files.notExists(path))
                return false;

            List<String> metas = new ArrayList<>();
            int metas_size = 0;

            try(Resources r = Resources.get();) {
                try(FileChannel fc = FileChannel.open(meta_path_path, READ); ) {
                    StringIOUtils.collect(ReadableByteChannelCustom.of(fc, r.buffer()), '\n', metas::add, r.decoder(), r.chars(), r.sb());
                }

                if(metas.isEmpty())
                    return false;

                try(FileChannel fc = FileChannel.open(meta_path_meta, READ)) {
                    ByteBuffer buf = r.buffer();
                    IOUtils.read(buf, true, fc);

                    final int BYTES = Long.BYTES + Integer.BYTES * 2 ;

                    for (String s : metas) {
                        if(buf.remaining() < BYTES) {
                            IOUtils.compactOrClear(buf);
                            IOUtils.read(buf, false, fc);        
                        }

                        MetaImpl m = new MetaImpl(buf.getInt(), buf.getInt(), s, buf.getLong());
                        this._metas.add(m);

                        int len = buf.getInt();
                        if(len < buf.remaining()) {
                            m.path_digest = Arrays.copyOfRange(buf.array(), buf.position(), buf.position() + len);
                            buf.position(buf.position() + len);
                        } else {
                            byte[] bytes = new byte[len];
                            for (int i = 0; i < bytes.length; i++) {
                                if(!buf.hasRemaining() && IOUtils.read(buf, true, fc) < 0)
                                    throw new EOFException();

                                bytes[i] = buf.get();
                            }

                            m.path_digest = bytes;
                        }
                    }
                }

                if(this._metas.isEmpty())
                    return false;

                metas_size = this._metas.size();
                ByteBuffer buffer = r.buffer();
                CharsetEncoder encoder = r.encoder();
                MessageDigest digester = digester();

                this._metas.removeIf(d -> {
                    try {
                        if(Arrays.equals(digest(d.sourceS, buffer, encoder, digester), d.path_digest))
                            return false;
                        else
                            logger.error("tempered meta found: {}", d);
                    } catch (CharacterCodingException | DigestException e) {
                        logger.error("failed to encode: \"{}\"", d.sourceS, e);
                    }
                    return true;
                });
            }
            
            if(this._metas.size() != metas_size){
                writeMetasPaths(WRITE_ALL);
                writeMetasMeta();
            }
            return true;
        } catch (Throwable e) {
            logger.error("failed to read app_meta", e);
            return false;
        }
    }

    private void writeMetasMeta() throws IOException, DigestException, NoSuchAlgorithmException {
        try(FileChannel fc = FileChannel.open(meta_path_path, WRITE, CREATE, TRUNCATE_EXISTING);
                Resources r = Resources.get();
                ) {
            
            synchronized (wbuffer) {
                ByteBuffer buffer = r.buffer();
                CharsetEncoder encoder = r.encoder();
                MessageDigest digester = digester();
                ByteBuffer buf2 = wbuffer.get();
                buf2.clear();
                
                final int BYTES = Long.BYTES + Integer.BYTES * 2 ;
                
                for (MetaImpl m : _metas) {
                    if(buffer.remaining() < BYTES)
                        IOUtils.write(buffer, fc, true);
                    
                    if(m.path_digest == null)
                        m.path_digest = digest(m.sourceS, buf2, encoder, digester);

                    byte[] digest = m.path_digest;
                    
                    buffer.putInt(m.id)
                    .putInt(m.order)
                    .putLong(m.last_modified)
                    .putInt(digest.length);
                    
                    if(buffer.remaining() < digest.length)
                        IOUtils.write(buffer, fc, true);
                    
                    buffer.put(digest);
                }
                
                IOUtils.write(buffer, fc, true);
            }
        }
    }

    private void writeMetasPaths(String path) throws IOException {
        Objects.requireNonNull(path);
        if(path != WRITE_ALL && Checker.isEmptyTrimmed(path)) 
                throw new IllegalArgumentException();
        
        try(FileChannel fc = FileChannel.open(meta_path_path, WRITE, CREATE, path == WRITE_ALL ? TRUNCATE_EXISTING : APPEND);
                Resources r = Resources.get();
                WriterImpl w = new WriterImpl(fc, r.buffer(), r.chars(), false, r.encoder())) {
            
            if(path == WRITE_ALL) {
                for (MetaImpl m : _metas) 
                    w.append(m.sourceS).append('\n');
            } else {
                w.append(path).append('\n');
            }
        }
    }

    private MessageDigest digester() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("md5");
    }

    private byte[] digest(String s, ByteBuffer buf, CharsetEncoder encoder, MessageDigest digester) throws CharacterCodingException, DigestException {
        buf.clear();
        buf = StringIOUtils.encode(s, buf, encoder);

        digester.reset();
        digester.update(buf.array(), 0, buf.remaining());
        return digester.digest();
    }

    @Override
    public RootEntryZ create(Path path) throws Exception {
        if(Files.exists(path))
            throw new IOException("file already exists: "+path);

        return create0(path.normalize().toAbsolutePath());
    }
    private RootEntryZ create0(Path path) throws Exception {
        // metas.set(0, cacheImpl);
        return Junk.notYetImplemented(); // return new RootEntryZ(m, path, this);
    }

    public  class MetaImpl implements Meta {
        private int order;
        public final int id;
        private long last_modified;
        private byte[] path_digest;
        private Path source;
        private final String sourceS;

        public MetaImpl(int id, int order, String source, long lasmod) {
            this.id = id;
            this.order = order;
            this.sourceS = source;
            if(Checker.isEmptyTrimmed(sourceS))
                throw new IllegalArgumentException("bad value for sourceS = \""+source+"\"");
            this.last_modified = lasmod;
        }
        @Override
        public int getId() {
            return id;
        }
        @Override
        public Path source() {
            if(source == null)
                source = Paths.get(sourceS);
            return source;
        }
        @Override
        public long getLastModified() {
            return last_modified;
        }
        private void updateLastModified() {
            this.last_modified = source().toFile().lastModified();
        }
        @Override
        public String toString() {
            return "MetaImpl [id=" + id + ", order=" + order + ", last_modified=" + last_modified + ", source=" + sourceS + "]";
        }
    }

    @Override
    public RootEntryZ load(Path path) throws Exception {
        if(!Files.isRegularFile(path))
            throw new IOException("file not found: "+path);

        path =  path.normalize().toAbsolutePath();
        int index = find(path);

        if(index < 0)
            return create0(path);

        MetaImpl m = _metas.get(index);

        if(active.get(m) != null)
            ThrowException.illegalAccessError();

        if(m.last_modified != path.toFile().lastModified()) {
            logger.debug("RESET CACHE: because: meta.lastModified({}) != path.toFile().lastModified({}),  path: {}", m.last_modified, path.toFile().lastModified(), path);
            return create0(path);
        }
        return new CacheImpl(m);
    }

    private class CacheImpl extends CachedRoot {
        private MetaImpl meta;

        public CacheImpl(MetaImpl meta) throws IOException {
            super(resolve(meta.id+".index"), resolve(meta.id+".content"));
            this.meta = meta;
            active.put(meta, this);
        }

        @Override
        protected void saveMeta() throws IOException {
            _metas.forEach(m -> {
                if(m == meta)
                    m.order = 0;
                else 
                    m.order++;
            });
            
            try {
                RootEntryZFactory.this.writeMetasMeta();
            } catch (DigestException | NoSuchAlgorithmException e) {
                logger.error("", e);
                return;
            }
            
            Path  p = meta.source();
            metas_sorted_paths.removeIf(m -> m == p);
            metas_sorted_paths.add(0, p);
        }

        @Override
        public Path getJbookPath() {
            return meta.source();
        }
        @Override
        protected void updateLastModified() {
            meta.updateLastModified();
        }

        @Override
        public void close() throws IOException {
            super.close();
            active.remove(meta);
            meta = null;
        }
    }

    private int find(Path path) {
        for (int i = 0; i < _metas.size(); i++) {
            if(path.equals(_metas.get(i).source()))
                return i;
        }
        return -1;
    }

    public Path resolve(String s) {
        return mydir.resolve(s);
    }

    @Override
    public List<Path> recentsFiles() {
        return Collections.unmodifiableList(metas_sorted_paths);
    }
}
