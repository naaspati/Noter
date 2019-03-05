package sam.noter.app;
import static javafx.scene.input.KeyCode.F;
import static javafx.scene.input.KeyCode.F4;
import static javafx.scene.input.KeyCode.N;
import static javafx.scene.input.KeyCode.O;
import static javafx.scene.input.KeyCode.S;
import static javafx.scene.input.KeyCode.W;
import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.fx.helpers.FxKeyCodeUtils.combination;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.noter.EnvKeys.OPEN_CMD_DIR;
import static sam.noter.EnvKeys.OPEN_CMD_ENABLE;
import static sam.noter.Utils2.fx;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codejargon.feather.Feather;
import org.codejargon.feather.Key;
import org.codejargon.feather.Provides;
import org.json.JSONException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Builder;
import javafx.util.BuilderFactory;
import sam.di.ConfigKey;
import sam.di.ConfigManager;
import sam.di.Injector;
import sam.di.OnExitQueue;
import sam.di.ParentWindow;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxBindings;
import sam.fx.helpers.FxFxml;
import sam.fx.helpers.FxHBox;
import sam.fx.helpers.FxMenu;
import sam.fx.popup.FxPopupShop;
import sam.io.IOUtils;
import sam.io.fileutils.DirWatcher;
import sam.io.fileutils.FileOpenerNE;
import sam.io.serilizers.StringWriter2;
import sam.myutils.Checker;
import sam.nopkg.Junk;
import sam.noter.BoundBooks;
import sam.noter.DyanamiMenus;
import sam.noter.EntryTreeItem;
import sam.noter.FilesLookup;
import sam.noter.Utils2;
import sam.noter.Utils2.FileChooserType;
import sam.noter.bookmark.BookmarksPane;
import sam.noter.bookmark.SearchBox;
import sam.noter.editor.Editor;
import sam.noter.tabs.Tab;
import sam.noter.tabs.TabContainer;
import sam.thread.MyUtilsThread;
public class App extends Application implements ChangeListener<Tab> {
	private final Logger logger = LogManager.getLogger(App.class);

	static {
		FxFxml.setFxmlDir(ClassLoader.getSystemResource("fxml"));
	}

	@FXML private BorderPane root;
	@FXML private SplitPane splitPane;
	@FXML private BookmarksPane bookmarks;
	@FXML private TabContainer tabsContainer;
	@FXML private Editor editor;

	private final SimpleObjectProperty<Path> currentFile = new SimpleObjectProperty<>();
	private final SimpleBooleanProperty searchActive = new SimpleBooleanProperty();
	private WeakReference<SearchBox> weakSearchBox = new WeakReference<SearchBox>(null);

	public static final ColorAdjust GRAYSCALE_EFFECT = new ColorAdjust();
	private Stage stage;
	private List<Runnable> onExit; 

	private ReadOnlyObjectProperty<Tab> currentTab;
	
	private BoundBooks boundBooks;
	private BooleanBinding currentTabNull;
	private Path appDataDir, backupDir; //FIXME init

	static {
		GRAYSCALE_EFFECT.setSaturation(-1);
	}
	
	private final Tools injector = new Tools();
	
	private class Tools implements Injector, OnExitQueue, ConfigManager {
		private final Feather feather;
		private int configMod;
		private EnumMap<ConfigKey, String> configs = new EnumMap<>(ConfigKey.class);
		
		public Tools() {
			this.feather = Feather.with(this);
		}
		
		@Override
		public Path appDir() {
			return appDataDir;
		}
		@Override
		public Path backupDir() {
			return backupDir;
		}
		@Override
		public String getConfig(ConfigKey key) {
			return configs.get(key);
		}
		@Override
		public void setConfig(ConfigKey key, String value) {
			Objects.requireNonNull(key);
			if(!Objects.equals(value, configs.get(key))) {
				configs.put(key, value);
				configMod++;
			}
		}
		
		@Override
		public <E, A extends Annotation> E instance(Class<E> type, Class<A> qualifier) {
			return feather.instance(Key.of(type, qualifier));
		}
		@Override
		public <E> E instance(Class<E> type) {
			return feather.instance(type);
		}
		
		@Provides
		public Injector injector( ) {
			return this;
		}
		@Provides
		public OnExitQueue onexit( ) {
			return this;
		}
		@Provides
		public ConfigManager cm( ) {
			return this;
		}
		
		@ParentWindow
		@Provides
		public Stage stage( ) {
			return stage;
		}

		@Override
		public void runOnExist(Runnable runnable) {
			if(onExit == null)
				onExit = Collections.synchronizedList(new ArrayList<>());
			onExit.add(runnable);
		}
		
		
	};

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);
		FileOpenerNE.setErrorHandler((file, error) -> FxAlert.showErrorDialog(file, "failed to open file", error));
		
		boundBooks = injector.instance(BoundBooks.class);

		FXMLLoader loader = new FXMLLoader(ClassLoader.getSystemResource("fxml/App.fxml"));
		loader.setBuilderFactory(new BuilderFactory() {
			@Override
			public Builder<?> getBuilder(Class<?> type) {
				return () -> injector.instance(type);
			}
		});
		loader.setRoot(stage);
		loader.setController(this);
		loader.load();
		
		currentTab = tabsContainer.currentTabProperty();
		currentTabNull = currentTab.isNull();

		splitPane.setDividerPositions(0, 0.2);
		splitPane.widthProperty().addListener((p, o, n) -> splitPane.setDividerPosition(0, 0.2));
		currentTab.addListener(this);
		root.setTop(getMenubar());

		loadIcon(stage);
		showStage(stage);
		readRecents();

		addTabs(getParameters().getRaw());
		watcher();
	}

	private void watcher() throws IOException {
		if(!OPEN_CMD_ENABLE)
			return;

		logger.debug(() -> "INIT: OPEN_CMD_DIR watcher");
		Files.createDirectories(OPEN_CMD_DIR);
		MyUtilsThread.runOnDeamonThread(new DirWatcher(OPEN_CMD_DIR, StandardWatchEventKinds.ENTRY_CREATE) {
			@Override
			protected void onEvent(Path context, WatchEvent<?> we) {
				try {
					Path p =  dir.resolve(context);
					addTabs(Files.readAllLines(p));
					Files.deleteIfExists(p);
				} catch (IOException e) {
					fx(() -> FxAlert.showErrorDialog(context, "failed to read", e));
				}
			}
			@Override
			protected boolean onErrorContinue(Exception e) {
				e.printStackTrace();
				return true;
			}
			@Override
			protected void failed(Exception e) {
				e.printStackTrace();
			}
		});
	}
	private void openByCmd() {
		TextInputDialog dialog = new TextInputDialog();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.initOwner(stage);
		dialog.setHeaderText("Enter CMD");
		dialog.setContentText("CMD");

		String s = dialog.showAndWait()
				.orElse(null);
		if(Checker.isEmptyTrimmed(s)) {
			FxPopupShop.showHidePopup("Cancelled", 1500);
			return;
		}

		List<String> list = new ArrayList<>();

		if(s.indexOf('"') >= 0) {
			Pattern pattern = Pattern.compile("\"(.+?)\"");
			Matcher m = pattern.matcher(s);
			StringBuffer sb = new StringBuffer();
			while(m.find())  {
				list.add(m.group(1));
				m.appendReplacement(sb, "");
			}
			m.appendTail(sb);
			for (String t : sb.toString().split("\\s+"))
				list.add(t);
		} else {
			list = Pattern.compile("\\s+").splitAsStream(s).collect(Collectors.toList());
		}
		addTabs(list);
	}

	private void addTabs(List<String> input) {
		fx(() -> {
			try {
				List<Path> files = new FilesLookup().parse(injector, appDataDir, input);
				if(files.isEmpty()) return;
				files.replaceAll(f -> f.normalize().toAbsolutePath());
				List<Path> paths = new ArrayList<>();
				tabsContainer.forEach(t -> paths.add(t.getJbookPath()));
				paths.removeIf(t -> t == null);
				if(!paths.isEmpty()){
					files.removeIf(paths::contains);
					if(files.isEmpty())
						return;
					files.removeIf(f -> paths.stream().anyMatch(g -> {
						try {
							return Files.isSameFile(f, g);
						} catch (IOException e) {
							System.out.println(e);
							return false;
						}
					}));
					if(files.isEmpty())
						return;
				}

				tabsContainer.addTabs(files);
				stage.toFront();

			} catch (IOException e) {
				FxAlert.showErrorDialog(String.join("\n", input), "failed to read", e);
			}
		});
	}

	private void loadIcon(Stage stage) throws IOException {
		Path p = Paths.get("notebook.png");
		if(Files.exists(p))
			stage.getIcons().add(new Image(Files.newInputStream(p)));
	}
	private void readRecents() throws IOException, URISyntaxException {
		Path p = appDataDir.resolve("recents.txt");
		if(Files.notExists(p))
			return;

		Files.lines(p)
		.map(String::trim)
		.filter(s -> !s.isEmpty())
		.map(Paths::get)
		.filter(Files::exists)
		.distinct()
		.map(this::recentsMenuItem)
		.forEach(recentsMenu.getItems()::add);
	}
	private void showStage(Stage stage2) throws IOException {
		Path p = stageSettingPath();

		Runnable r = () -> {
			Rectangle2D screen = Screen.getPrimary().getBounds();

			stage.setWidth(screen.getWidth()/2);
			stage.setHeight(screen.getHeight());
			stage.setX(0d);
			stage.setY(0d);
		} ;

		if(Files.notExists(p)) {
			r.run();
		} else {
			ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * 4);
			try(FileChannel fc = FileChannel.open(p, StandardOpenOption.READ)) {
				IOUtils.read(buffer, true, fc);
				if(buffer.remaining() < buffer.capacity())
					r.run();
				else {
					stage.setX(buffer.getDouble());
					stage.setY(buffer.getDouble());
					stage.setWidth(buffer.getDouble());
					stage.setHeight(buffer.getDouble());
				}
			} catch (IOException e2) {
				e2.printStackTrace();
				r.run();
			}
		}

		try {
			stage.getIcons().add(new Image("notebook.png"));
		} catch (Exception e2) {}

		stage.show();
		stage.setOnCloseRequest(e -> {
			exit();
			e.consume();
		});
	}
	private MenuItem recentsMenuItem(Path path) {
		MenuItem mi =  menuitem(path.toString(), e -> tabsContainer.open(Collections.singletonList(path), recentsMenu));
		mi.getStyleClass().add("recent-mi");
		mi.setUserData(path);
		return mi;
	}

	private Tab getCurrentTab() {
		return currentTab.get();
	}
	private void exit() {
		if(onExit != null)
			onExit.forEach(Runnable::run);
		
		if(tabsContainer.closeAll()) {
			try(FileChannel fc = FileChannel.open(stageSettingPath(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * 4);

				buffer.putDouble(stage.getX()) 
				.putDouble(stage.getY()) 
				.putDouble(stage.getWidth()) 
				.putDouble(stage.getHeight())
				.flip();

				fc.write(buffer);
			} catch (IOException e) {
				logger.fatal("failed to write: {}", stageSettingPath(), e);
			}

			try {
				Files.write(appDataDir.resolve("recents.txt"), recentsMenu.getItems().stream()
						.map(MenuItem::getUserData)
						.map(Object::toString)
						.map(s -> s.replace('\\', '/'))
						.collect(Collectors.toList()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				logger.fatal("failed to save: recents.txt  ", e);   
			}
			Platform.exit();
		}
	}
	private Path stageSettingPath() {
		return appDataDir.resolve("stage.settings");
	}

	private MenuBar getMenubar() throws JSONException, IOException {
		MenuBar bar =  new MenuBar(
				getFileMenu(), 
				bookmarks.getBookmarkMenu(), 
				getSearchMenu(), 
				editorMenu()
				);

		new DyanamiMenus().load(bar, editor);
		return bar;
	}
	private Menu editorMenu() {
		Menu menu = editor.getEditorMenu();
		menu.getItems().add(FxMenu.menuitem("combine everything", this::combineEverything, currentTabNull));
		return menu;
	}

	//TODO
	private void combineEverything(ActionEvent e) {
		Tab tab = currentTab.get();
		StringBuilder sb = new StringBuilder(5000);
		separator = new char[0];
		walk(bookmarks.getRoot().getChildren(), sb, "");

		Scene previous = stage.getScene();
		Hyperlink link = new Hyperlink("<< BACK");

		link.setOnAction(e1 -> {
			stage.hide();
			stage.setScene(previous);
			stage.show();
		});

		Text ta = new Text(sb.toString());
		ScrollPane sp = new ScrollPane(ta);
		sp.setPadding(new Insets(0, 0, 0, 5));
		sp.setStyle("-fx-background:white");

		int lines = 0;
		for (int i = 0; i < sb.length(); i++) {
			if(sb.charAt(i) == '\n')
				lines++;
		}
		Button save = new Button("save");
		save.setOnAction(e1 -> {
			File file = Utils2.chooseFile(stage, "save in text", null, tab.getTabTitle().concat(".txt"), FileChooserType.SAVE);
			if(file == null) {
				FxPopupShop.showHidePopup("cancelled", 1500);
				return;
			} 
			try {
				StringWriter2.setText(file.toPath(), ta.getText());
			} catch (IOException e2) {
				FxAlert.showErrorDialog(file, "failed to save", e2);
			}
		});
		Text t = new Text();
		t.textProperty().bind(FxBindings.map(sp.vvalueProperty(), s -> String.valueOf((int)(s.doubleValue()*100))));
		HBox box = new HBox(10,link, FxHBox.maxPane(), save, new Text("lines: "+lines+", chars: "+sb.length()+", scroll:"), t, new Text());
		box.setAlignment(Pos.CENTER_RIGHT);
		box.setStyle("-fx-font-size:0.8em;");

		BorderPane root = new BorderPane(sp, box, null, null, null);
		Font f = Editor.getFont();
		root.setStyle(String.format("-fx-font-size:%s;-fx-font-family:%s;-fx-font-style:normal;", f.getSize(), f.getFamily(), f.getStyle()));

		separator = null;
		sb = null;
		stage.hide();
		stage.setScene(new Scene(root, Color.WHITE));
		stage.show();
		fx(System::gc);
	}
	private char[] separator;
	private char[] separator(int size) {
		if(separator.length >= size)
			return separator;

		separator = new char[size+10];
		Arrays.fill(separator, '#');

		return separator;
	}
	private void walk(List<TreeItem<String>> children, StringBuilder sb, String parent) {
		for (TreeItem<String> t : children) {
			EntryTreeItem e = (EntryTreeItem)t;
			String tag = parent.concat(e.getTitle());
			int n = sb.length();
			sb.append('|').append(separator(tag.length()+3), 0, tag.length()+3).append('|').append('\n');
			int n2 = sb.length();
			sb.append('|').append(' ').append(tag).append(' ').append(' ').append('|').append('\n')
			.append(sb, n, n2);

			sb.append(e.getContent());
			sb.append('\n').append('\n');

			List<TreeItem<String>> list = t.getChildren();
			if(!list.isEmpty())
				walk(list, sb, tag.concat(" > "));
		}
	}

	private Menu getDebugMenu() {
		return new Menu("debug", null,
				menuitem("no content bookmarks", e_e -> {
					StringBuilder sb = new StringBuilder();
					/* TODO
					 * getCurrentTab().walk(e -> {
							if(e.getContent() == null || e.getContent().trim().isEmpty()) {
								e.setExpanded(true);
								sb.append(e.toTreeString(false));							
							}
						});
					 */
					FxClipboard.setString(sb.toString());

					FxAlert.alertBuilder(AlertType.INFORMATION)
					.expandableText(sb)
					.expanded(true)
					.header("No Content Bookmarks")
					.showAndWait();
				}, currentTabNull),
				menuitem("ProgramName", e -> FxAlert.showMessageDialog("ManagementFactory.getRuntimeMXBean().getName()", ManagementFactory.getRuntimeMXBean().getName())),
				menuitem("Memory usage", e -> {
					FxAlert.alertBuilder(AlertType.INFORMATION)
					.content(new TextArea(Junk.memoryUsage()))
					.header("Memory Usage")
					.owner(stage)
					.show();
				})
				);
	}
	private Menu getSearchMenu() {
		Menu menu = new Menu("_Search", null, 
				menuitem("Search", combination(F, SHORTCUT_DOWN), e -> {
					tabsContainer.setDisable(true);
					tabsContainer.setEffect(GRAYSCALE_EFFECT);
					searchActive.set(true);

					SearchBox sb = weakSearchBox.get();

					if(sb == null) {
						sb = new SearchBox(stage, bookmarks, getCurrentTab());
						weakSearchBox = new WeakReference<>(sb);
						sb.setOnHidden(e_e -> {
							tabsContainer.setDisable(false);
							tabsContainer.setEffect(null);
							searchActive.set(false);
						});
					} else { 
						sb.start(getCurrentTab());
					}

				}, currentTabNull)
				);
		menu.setDisable(true);
		// TODO
		return menu;
	}

	private final Menu recentsMenu = new Menu("_Recents");
	private Menu getFileMenu() {
		recentsMenu.disableProperty().bind(searchActive.or(Bindings.isEmpty(recentsMenu.getItems())));

		BooleanBinding fileNull = currentFile.isNull().or(searchActive);
		BooleanBinding tabNull = currentTabNull.or(searchActive);
		BooleanBinding selectedZero = tabsContainer.tabsCountProperty().isEqualTo(0).or(searchActive);

		Menu closeSpecific = 
				new Menu("Close specific", null,
						menuitem("other tab(s)", e -> tabsContainer.closeExcept(getCurrentTab())),
						menuitem("tab(s) to the right", e -> tabsContainer.closeRightLeft(getCurrentTab(), true)),
						menuitem("tab(s) to the left", e -> tabsContainer.closeRightLeft(getCurrentTab(), false))
						);

		Menu menu = new Menu("_File", null, 
				menuitem("_New", combination(N, SHORTCUT_DOWN, ALT_DOWN), e -> tabsContainer.addBlankTab(), searchActive),
				menuitem("_Open...", combination(O, SHORTCUT_DOWN), e -> tabsContainer.open(null, recentsMenu), searchActive),
				recentsMenu,
				menuitem("Open Containing Folder", e -> getCurrentTab().open_containing_folder(getHostServices()), fileNull),
				menuitem("Reload From Disk", e -> getCurrentTab().reload_from_disk(), fileNull),
				menuitem("_Save", combination(S, SHORTCUT_DOWN), e -> getCurrentTab().save(false), fileNull),
				menuitem("Save _As", combination(S, SHORTCUT_DOWN, SHIFT_DOWN), e -> {getCurrentTab().save_as(false);updateCurrentFile();}, tabNull),
				menuitem("Sav_e All", combination(S, SHORTCUT_DOWN, ALT_DOWN), e -> {tabsContainer.saveAllTabs();updateCurrentFile();}, tabNull),
				menuitem("Rename", e -> {getCurrentTab().rename(); updateCurrentFile();}, fileNull),
				menuitem("Bind Book", e -> {boundBooks.bindBook(getCurrentTab());}, fileNull),
				new SeparatorMenuItem(),
				menuitem("_Close",combination(W, SHORTCUT_DOWN), e -> tabsContainer.closeTab(getCurrentTab()), selectedZero),
				menuitem("Close All",combination(W, SHORTCUT_DOWN, SHIFT_DOWN), e -> tabsContainer.closeAll(), selectedZero),
				closeSpecific,
				new SeparatorMenuItem(),
				menuitem("E_xit", combination(F4, ALT_DOWN), e -> exit())
				);

		if(!OPEN_CMD_ENABLE)
			menu.getItems().add(2,  menuitem("Open By Cmd", combination(O, SHORTCUT_DOWN, SHIFT_DOWN), e -> openByCmd(), searchActive));

		closeSpecific.disableProperty().bind(tabsContainer.tabsCountProperty().lessThan(2).or(searchActive));
		return menu;
	}
	private void updateCurrentFile() {
		Tab t = getCurrentTab(); 
		if(t == null)
			return;

		currentFile.set(t.getJbookPath());
		setTitle(t);
	}

	private void setTitle(Tab t) {
		stage.setTitle(t == null || t.getJbookPath() == null ? null : t.getJbookPath().toString());
	}

	public Editor editor() {
		return editor;
	}
	@Override
	public void changed(ObservableValue<? extends Tab> observable, Tab oldValue, Tab newTab) {
		boolean b = newTab == null;

		setTitle(newTab);
		currentFile.set(b ? null : newTab.getJbookPath());

		if(oldValue == null) return;
		Path p = oldValue.getJbookPath();
		if(p == null)
			return;

		List<MenuItem> list = recentsMenu.getItems(); 

		list.add(0, recentsMenuItem(p));
		if(list.size() > 10)
			list.subList(10, list.size()).clear();
	}

}
