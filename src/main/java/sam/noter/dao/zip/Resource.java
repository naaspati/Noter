package sam.noter.dao.zip;

import static java.nio.charset.CodingErrorAction.REPORT;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sam.io.IOConstants;
import sam.io.IOUtils;
import sam.reference.ReferenceUtils;
import sam.reference.WeakAndLazy;

public final class Resource implements AutoCloseable {
	private static final AtomicInteger count = new AtomicInteger(0);
	private static final AtomicReference<WeakReference<Resource>> reference = new AtomicReference<>();
	private static final Logger LOGGER = LogManager.getLogger(Resource.class);

	private Resource() { }

	public static Resource get() throws IOException {
		Resource resource = ReferenceUtils.get(reference.getAndSet(null));
		if(resource == null) {
			resource = new Resource();
			int n = count.incrementAndGet();
			LOGGER.debug(() -> n+": Resource created for: "+Thread.currentThread().getStackTrace()[2]);
		}

		resource.open();
		LOGGER.debug(() -> "Resource opened at: "+Thread.currentThread().getStackTrace()[2]);
		return resource;
	}

	public final byte[] bytes = new byte[IOConstants.defaultBufferSize()];
	/**
	 * wrap of bytes
	 */
	public final ByteBuffer buffer = ByteBuffer.wrap(bytes);
	public final CharBuffer chars = CharBuffer.allocate(100);
	public final WeakAndLazy<StringBuilder> wsink = new WeakAndLazy<>(StringBuilder::new);

	public final Charset CHARSET = StandardCharsets.UTF_8; 
	public final CharsetDecoder decoder = CHARSET.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);
	public final CharsetEncoder encoder = CHARSET.newEncoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT);

	private void open() throws IOException {
		IOUtils.ensureCleared(buffer);
		IOUtils.ensureCleared(chars);
		boolean[] b = {false};
		wsink.ifPresent(s -> b[0] = s.length() != 0);

		if(b[0])
			throw new IOException();
	}

	@Override
	public void close() throws IOException {

		buffer.clear();
		chars.clear();
		wsink.ifPresent(s -> s.setLength(0));
		decoder.reset();
		encoder.reset();

		reference.compareAndSet(null, new WeakReference<>(this));
	}
	public StringBuilder sb() {
		return wsink.get();
	}
}
