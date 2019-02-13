package sam.noter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import sam.io.serilizers.StringReader2;
import sam.io.serilizers.StringWriter2;
import sam.myutils.ErrorRunnable;
import sam.myutils.System2;
import sam.noter.dao.Entry;
import sam.reference.WeakPool;

public class Utils {
	private static final Logger logger = LogManager.getLogger(Utils.class);

	private static final List<Runnable> onStop = new ArrayList<>();
	public static final Path APP_DATA = EnvKeys.APP_DATA;
	public static final Path TEMP_DIR = APP_DATA.resolve("java_temp");
	public static final int TEMP_DIR_COUNT = TEMP_DIR.getNameCount();
	
	public static final WeakPool<byte[]> wwbytes = new WeakPool<>(true, () -> new byte[IOConstants.defaultBufferSize()]);
	public static final WeakPool<ByteBuffer> wbuff = new WeakPool<>(true, () -> ByteBuffer.allocate(IOConstants.defaultBufferSize()));
	public static final WeakPool<StringBuilder> wsb = new WeakPool<>(true, StringBuilder::new);
	public static final WeakPool<CharBuffer> wcharBuff = new WeakPool<>(true, () -> CharBuffer.allocate(100));
	public static final WeakPool<CharsetDecoder> wdecoder = new WeakPool<>(true, () -> StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT));

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
	private static final Path last_visited_save = APP_DATA.resolve("last-visited-folder.txt");
	private static File last_visited;

	public static File chooseFile(String title, File expectedDir, String expectedFilename, FileChooserType type) {
		Objects.requireNonNull(type);

		FileChooser chooser = new FileChooser();
		chooser.setTitle(title);
		chooser.getExtensionFilters().add(new ExtensionFilter("jbook file", "*.jbook"));
		Window parent = Session.global().get(Stage.class);

		if(expectedDir == null || !expectedDir.isDirectory()){
			if(last_visited != null)
				expectedDir = last_visited;
			else {
				try {
					expectedDir = Files.exists(last_visited_save) ? new File(StringReader2.getText(last_visited_save)) : null;
				} catch (IOException e) {
					logger.warn("failed to read: {}", last_visited_save, e);
					expectedDir = null;
				}
			}
		}

		if(expectedDir != null && expectedDir.isDirectory())
			chooser.setInitialDirectory(expectedDir);

		if(expectedFilename != null)
			chooser.setInitialFileName(expectedFilename);

		File file = type == FileChooserType.OPEN ? chooser.showOpenDialog(parent) : chooser.showSaveDialog(parent);

		if(file != null) {
			try {
				last_visited = file.getParentFile();
				StringWriter2.setText(last_visited_save, last_visited.toString().replace('\\', '/'));
			} catch (IOException e) {}
		}
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

	public static long pipe(InputStream is, Path path, byte[] buffer) throws IOException {
		try(OutputStream out = Files.newOutputStream(path)) {
			int n = 0;
			long size = 0;
			while((n = is.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				size += n;
			}
			return size;
		}
	}
	public static long pipe(InputStream in, FileChannel out, byte[] buffer) throws IOException {
		int n = 0;
		long size = 0;
		while((n = in.read(buffer)) > 0) {
			out.write(ByteBuffer.wrap(buffer, 0, n));
			size += n;
		}
		return size;
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
					if (cr.isUnderflow()) {
						int n = 0;
						while(n++ < 3) {
							cr = decoder.flush(chars);
							append(sb, chars);
							if(cr.isUnderflow())
								break loop;
							else if(!cr.isOverflow())
								cr.throwException();
						}
						throw new IOException("failed to parse all bytes");
					} else if (cr.isOverflow()) 
						append(sb, chars);
					else
						cr.throwException();
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
