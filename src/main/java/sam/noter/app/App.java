package sam.noter.app;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.noter.Utils.fx;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.slf4j.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Builder;
import javafx.util.BuilderFactory;
import sam.di.AppConfig;
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
import sam.myutils.Checker;
import sam.nopkg.EnsureSingleton;
import sam.nopkg.Junk;
import sam.noter.BoundBooks;
import sam.noter.DyanamiMenus;
import sam.noter.EntryTreeItem;
import sam.noter.FilesLookup;
import sam.noter.Utils;
import sam.noter.bookmark.BookmarksPane;
import sam.noter.dao.RootEntryFactory;
import sam.noter.dao.api.IRootEntry;
import sam.noter.editor.Editor;
import sam.noter.tabs.TabBox;
import sam.reference.WeakAndLazy;
import sam.thread.MyUtilsThread;
public class App extends Application implements AppUtilsImpl, DialogHelper, Observables, ChangeListener<IRootEntry> {
	private static final EnsureSingleton singleons = new EnsureSingleton();

	private final Logger logger = Utils.logger(App.class);

	{
		singleons.init();
	}

	static {
		FxFxml.setFxmlDir(ClassLoader.getSystemResource("fxml"));
	}

	public static final ColorAdjust GRAYSCALE_EFFECT = new ColorAdjust();

	private InjectorImpl injector;
	private BoundBooks boundBooks;

	@Override
	public void init() throws Exception {
		injector = new InjectorImpl(this);
		boundBooks = injector.instance(BoundBooks.class);
		GRAYSCALE_EFFECT.setSaturation(-1);
	}

	private StackPane stacks;
	
	@FXML private BorderPane root;
	@FXML private SplitPane splitPane;
	@FXML private BookmarksPane bookmarks;
	@FXML private TabBox tabsBox;
	@FXML private Editor editor;

	private final SimpleObjectProperty<Path> currentFile = new SimpleObjectProperty<>();
	private final SimpleBooleanProperty searchActive = new SimpleBooleanProperty();

	private Stage stage;

	private final SimpleBooleanProperty currentTabNull = new SimpleBooleanProperty();
	private IRootEntry currentRoot;

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);
		FileOpenerNE.setErrorHandler((file, error) -> FxAlert.showErrorDialog(file, "failed to open file", error));

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

		currentRootEntryProperty().addListener(this);

		splitPane.setDividerPositions(0, 0.2);
		splitPane.widthProperty().addListener((p, o, n) -> splitPane.setDividerPosition(0, 0.2));
		currentRootEntryProperty().addListener(this);
		root.setTop(getMenubar());

		loadIcon(stage);
		showStage(stage);

		addTabs(getParameters().getRaw());
		watcher();
	}

	private void watcher() throws IOException {
		Path ocd = (Path) System.getProperties().get("OPEN_CMD_DIR");
		if(ocd == null)
			return;

		logger.debug("INIT: OPEN_CMD_DIR watcher: {}", ocd);
		Files.createDirectories(ocd);

		MyUtilsThread.runOnDeamonThread(new DirWatcher(ocd, StandardWatchEventKinds.ENTRY_CREATE) {
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
				List<Path> files = new FilesLookup().parse(injector, input);
				if(files.isEmpty()) return;
				files.replaceAll(f -> f.normalize().toAbsolutePath());
				List<Path> paths = new ArrayList<>();
				tabsBox.forEach(t -> paths.add(t.getJbookPath()));
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

				tabsBox.addTabs(files);
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

	private void exit() {
		if(injector.onExit != null)
			injector.onExit.forEach(Runnable::run);

		if(tabsBox.closeAll()) {
			try(FileChannel fc = FileChannel.open(stageSettingPath(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES * 4);

				buffer.putDouble(stage.getX()) 
				.putDouble(stage.getY()) 
				.putDouble(stage.getWidth()) 
				.putDouble(stage.getHeight())
				.flip();

				fc.write(buffer);
			} catch (IOException e) {
				logger.error("failed to write: {}", stageSettingPath(), e);
			}
			Platform.exit();
		}
	}
	private Path stageSettingPath() {
		return injector.appDataDir.resolve("stage.settings");
	}

	private MenuBar getMenubar() throws JSONException, IOException {
		MenuBar bar =  new MenuBar(
				getFileMenu(), 
				bookmarks.getBookmarkMenu(), 
				//TODO getSearchMenu(), 
				editorMenu()
				);

		new DyanamiMenus().load(bar, editor, injector);
		return bar;
	}
	private Menu editorMenu() {
		Menu menu = editor.getEditorMenu();
		menu.getItems().add(FxMenu.menuitem("combine everything", this::combineEverything, currentTabNull));
		return menu;
	}

	//TODO
	private void combineEverything(ActionEvent e) {
		IRootEntry tab = currentRoot;
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
			File file = chooseFile("save in text", null, tab.getJbookPath().getFileName()+ ".txt", FileChooserHelper.Type.SAVE, null);
			if(file == null) {
				FxPopupShop.showHidePopup("cancelled", 1500);
				return;
			} 
			Utils.writeTextHandled(ta.getText(), file.toPath());
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
					 * currentRoot.walk(e -> {
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
	/* TODO 
	 * 	private Menu getSearchMenu() {
		Menu menu = new Menu("_Search", null, 
				menuitem("Search", combination(F, SHORTCUT_DOWN), e -> {
					tabsContainer.setDisable(true);
					tabsContainer.setEffect(GRAYSCALE_EFFECT);
					searchActive.set(true);

					SearchBox sb = weakSearchBox.get();

					if(sb == null) {
						sb = new SearchBox(stage, bookmarks, currentRoot);
						weakSearchBox = new WeakReference<>(sb);
						sb.setOnHidden(e_e -> {
							tabsContainer.setDisable(false);
							tabsContainer.setEffect(null);
							searchActive.set(false);
						});
					} else { 
						sb.start(currentRoot);
					}

				}, currentTabNull)
				);
		menu.setDisable(true);
		return menu;
	} 
	 */

	private final Menu recentsMenu = new Menu("_Recents", null, new MenuItem()); // first menuitem is needed to file onshowing event
	private Menu getFileMenu() {
		BooleanBinding fileNull = currentFile.isNull().or(searchActive);
		BooleanBinding tabNull = currentTabNull.or(searchActive);
		BooleanBinding selectedZero = tabsBox.tabsCountProperty().isEqualTo(0).or(searchActive);

		Menu closeSpecific = 
				new Menu("Close specific", null,
						menuitem("other tab(s)", e -> tabsBox.closeExcept(currentRoot)),
						menuitem("tab(s) to the right", e -> tabsBox.closeRightLeft(currentRoot, true)),
						menuitem("tab(s) to the left", e -> tabsBox.closeRightLeft(currentRoot, false))
						);

		Menu menu = new Menu("_File");
		recentsMenu.disableProperty().bind(searchActive.or(Bindings.isEmpty(recentsMenu.getItems())));

		recentsMenu.setOnShowing(e -> {
			recentsMenu.setOnShowing(null);
			loadRecents(recentsMenu);
		});

		/*
		new Menu("_File", null, 
				menuitem("_New", combination(N, SHORTCUT_DOWN, ALT_DOWN), e -> tabsBox.addBlankTab(), searchActive),
				menuitem("_Open...", combination(O, SHORTCUT_DOWN), e -> tabsBox.open(null, recentsMenu), searchActive),
				recentsMenu,
				menuitem("Open Containing Folder", e -> currentRoot.open_containing_folder(getHostServices()), fileNull),
				menuitem("Reload From Disk", e -> currentRoot.reload_from_disk(), fileNull),
				menuitem("_Save", combination(S, SHORTCUT_DOWN), e -> currentRoot.save(false), fileNull),
				menuitem("Save _As", combination(S, SHORTCUT_DOWN, SHIFT_DOWN), e -> {currentRoot.save_as(false);updateCurrentFile();}, tabNull),
				menuitem("Sav_e All", combination(S, SHORTCUT_DOWN, ALT_DOWN), e -> {tabsBox.saveAllTabs();updateCurrentFile();}, tabNull),
				menuitem("Rename", e -> {currentRoot.rename(); updateCurrentFile();}, fileNull),
				menuitem("Bind Book", e -> {boundBooks.bindBook(currentRoot);}, fileNull),
				new SeparatorMenuItem(),
				menuitem("_Close",combination(W, SHORTCUT_DOWN), e -> tabsBox.closeTab(currentRoot), selectedZero),
				menuitem("Close All",combination(W, SHORTCUT_DOWN, SHIFT_DOWN), e -> tabsBox.closeAll(), selectedZero),
				closeSpecific,
				new SeparatorMenuItem(),
				menuitem("E_xit", combination(F4, ALT_DOWN), e -> exit())
				);

				if(!OPEN_CMD_ENABLE)
			menu.getItems().add(2,  menuitem("Open By Cmd", combination(O, SHORTCUT_DOWN, SHIFT_DOWN), e -> openByCmd(), searchActive));
		 */

		closeSpecific.disableProperty().bind(tabsBox.tabsCountProperty().lessThan(2).or(searchActive));
		return menu;
	}
	private void loadRecents(Menu m) {
		List<Path> list = injector.instance(RootEntryFactory.class)
				.recentsFiles(); 

		m.getItems().clear();		
		list.forEach(p -> m.getItems().add(recentsMenuItem(p)));
	}
	private MenuItem recentsMenuItem(Path path) {
		MenuItem mi =  menuitem(path.toString(), e -> tabsBox.open(Collections.singletonList(path), (o, f) -> recentsMenu.getItems().remove(e.getSource())));
		mi.getStyleClass().add("recent-mi");
		mi.setUserData(path);
		return mi;
	}

	private void updateCurrentFile() {
		if(currentRoot == null)
			return;

		currentFile.set(currentRoot.getJbookPath());
		setTitle(currentRoot);
	}
	private void setTitle(IRootEntry t) {
		stage.setTitle(Optional.ofNullable(t).map(IRootEntry::getJbookPath).map(Path::toString).orElse(null));
	}

	public Editor editor() {
		return editor;
	}
	@Override
	public void changed(ObservableValue<? extends IRootEntry> observable, IRootEntry oldValue, IRootEntry newValue) {
		boolean b = newValue == null;
		currentRoot = newValue;
		currentTabNull.set(b);

		setTitle(newValue);
		currentFile.set(b ? null : newValue.getJbookPath());

		if(oldValue == null) return;
		Path p = oldValue.getJbookPath();
		if(p == null)
			return;

		List<MenuItem> list = recentsMenu.getItems(); 

		list.add(0, recentsMenuItem(p));
		if(list.size() > 10)
			list.subList(10, list.size()).clear();
	}

	@Override public Window stage() { return stage; }
	@Override public AppConfig config() { return injector; }

	private final WeakAndLazy<FileChooser> fchooser = new WeakAndLazy<>(FileChooser::new);
	@Override public FileChooser newFileChooser() { return fchooser.get(); }
	
	private class LayeredDialog implements Runnable {
		private final BorderPane root = new BorderPane();
		private final Group group = new Group(root);
		private final Button close = new Button("close");
		private final EventHandler<ActionEvent> event = e -> run(); 
		
		public LayeredDialog() {
			root.setTop(close);
		}
		
		@Override
		public void run() {
			close.setOnAction(null);
			root.setCenter(null);
			stacks.getChildren().remove(group);
		}
		boolean set(Node node) {
			if(root.getCenter() != null)
				return false;
			
			close.setOnAction(event);
			root.setCenter(node);
			stacks.getChildren().add(group);
			return true;
		}
	}

	private WeakAndLazy<LayeredDialog> wlayer = new WeakAndLazy<>(LayeredDialog::new); 
	
	@Override
	public Runnable showDialog(Node view) {
		Objects.requireNonNull(view);
		
		if(stacks == null) {
			stacks = new StackPane();
			
			Node node = stage.getScene().getRoot();
			stage.getScene().setRoot(stacks);
			stacks.getChildren().add(node);
			node.disableProperty().bind(Bindings.size(stacks.getChildren()).isNotEqualTo(1));
		}
		
		LayeredDialog dialog = wlayer.get();
		if(!dialog.set(view)) {
			dialog = new LayeredDialog();
			dialog.set(view);
		}
		
		return dialog;
	}

	@Override
	public ObservableValue<IRootEntry> currentRootEntryProperty() {
		return tabsBox.selectedItemProperty();
	}
	@Override
	public ObservableValue<EntryTreeItem> currentItemProperty() {
		return bookmarks.selectedItemProperty();
	}
}
