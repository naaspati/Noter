package sam.noter;

import static java.nio.charset.CodingErrorAction.REPORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static sam.io.IOUtils.*;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.Window;
import sam.config.Session;
import sam.fx.clipboard.FxClipboard;
import sam.fx.popup.FxPopupShop;
import sam.io.IOConstants;
import sam.io.infile.DataMeta;
import sam.io.infile.InFile;
import sam.myutils.Checker;
import sam.myutils.ErrorRunnable;
import sam.myutils.System2;
import sam.nopkg.SavedAsStringResource;
import sam.noter.dao.Entry;
import sam.reference.WeakPool;

public class Utils {
	private static final Logger logger = LogManager.getLogger(Utils.class);

	private static final List<Runnable> onStop = new ArrayList<>();
	public static final Path APP_DATA = EnvKeys.APP_DATA;
	public static final Path TEMP_DIR = APP_DATA.resolve("java_temp");
	public static final int TEMP_DIR_COUNT = TEMP_DIR.getNameCount();
	public static final Charset CHARSET = UTF_8;
	
	public static final WeakPool<byte[]> wwbytes = new WeakPool<>(true, () -> new byte[IOConstants.defaultBufferSize()]);
	public static final WeakPool<ByteBuffer> wbuff = new WeakPool<>(true, () -> ByteBuffer.allocate(IOConstants.defaultBufferSize()));
	public static final WeakPool<StringBuilder> wsb = new WeakPool<>(true, StringBuilder::new);
	public static final WeakPool<CharBuffer> wcharBuff = new WeakPool<>(true, () -> CharBuffer.allocate(100));
	public static final WeakPool<CharsetDecoder> wdecoder = new WeakPool<>(true, () -> CHARSET.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT));
	public static final WeakPool<CharsetEncoder> wencoder = new WeakPool<>(true, () -> CHARSET.newEncoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT));

	static {
		String s = System2.lookup("session_file");
		if(s == null)
			System.setProperty("session_file", APP_DATA.resolve("session.properties").toString());
	}
	public static void init() {/*init static block */}

	private Utils() {}

	public enum FileChooserType {
		OPEN, SAVE
	}
	private static final SavedAsStringResource<File> last_visited = new SavedAsStringResource<>(APP_DATA.resolve("last-visited-folder.txt"), File::new);

	public static File chooseFile(String title, File expectedDir, String expectedFilename, FileChooserType type) {
		Objects.requireNonNull(type);

		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));
		Window parent = Session.global().get(Stage.class);

		if(expectedDir == null || !expectedDir.isDirectory())
			expectedDir = last_visited.get();

		if(expectedDir != null && expectedDir.isDirectory())
			chooser.setInitialDirectory(expectedDir);

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == FileChooserType.OPEN ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);

		if(file != null) 
			last_visited.set(file.getParentFile());
		return file;
	}
	public static Entry castEntry(TreeItem<String> parent) {
		return (Entry)parent;
	}
	public static void addOnStop(Runnable action) {
		onStop.add(action);
	}
	public static void stop() {
		onStop.forEach(Runnable::run);
	}

	public static void copyToClipboard(String s) {
		FxClipboard.setString(s);
		FxPopupShop.showHidePopup(s, 2000);
	}
	public static void fx(Runnable runnable) {
		Platform.runLater(runnable);
	}
	//for debug perposes
	public static String subpathWithPrefix(Path p) {
		if(p.getNameCount() > TEMP_DIR_COUNT && p.startsWith(TEMP_DIR))
			return "tempDir://"+p.subpath(TEMP_DIR_COUNT, p.getNameCount());
		return p.toString();
	}

	public static <E> E get(Logger logger, Callable<E> call, E defaultValue)  {
		try {
			return call.call();
		} catch (Exception e) {
			logger.catching(e);
		}
		return defaultValue;
	}
	public static boolean hide(Logger logger, ErrorRunnable call)  {
		try {
			call.run();
			return true;
		} catch (Exception e) {
			logger.catching(e);
			return false;
		}
	}
	public static DataMeta encodeNWrite(CharSequence content, InFile file) throws IOException {
		if(Checker.isEmpty(content))
			return null;
		
		CharsetEncoder encoder = wencoder.poll();
		ByteBuffer buffer = wbuff.poll();
		buffer.clear();
		
		try {
			return file.write(content, buffer, encoder);
		} finally {
			encoder.reset();
			buffer.clear();
			
			wencoder.add(encoder);
			wbuff.add(buffer);
			
		}
	}

	

	public static String decode(ByteBuffer buffer) throws IOException {
			if(buffer.remaining() == 0)
				return "";

			StringBuilder sb = wsb.poll();
			CharBuffer chars = wcharBuff.poll();
			CharsetDecoder decoder = wdecoder.poll();

			try {
				decoder.reset();
				loop:
				while(true) {
					CoderResult cr = buffer.hasRemaining() ? decoder.decode(buffer, chars, true) : CoderResult.UNDERFLOW;
					check(cr);
					
					if (cr.isUnderflow()) {
						int n = 0;
						while(n++ < 3) {
							cr = decoder.flush(chars);
							check(cr);
							append(sb, chars);
							
							if(cr.isUnderflow())
								break loop;
						}
						throw new IOException("failed to parse all bytes");
					} else if (cr.isOverflow()) {
						append(sb, chars);
					} 
				}
				return sb.toString();
			} finally {
				sb.setLength(0);
				chars.clear();
				decoder.reset();

				wsb.add(sb);
				wcharBuff.add(chars);
				wdecoder.add(decoder);
			}
	}
	

	private static void append(StringBuilder sb, CharBuffer chars) {
		chars.flip();
		sb.append(chars);
		chars.clear();
	}
}
