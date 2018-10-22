package sam.apps.jbook_reader;

import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.apps.jbook_reader.Utils.APP_DATA;
import static sam.fx.helpers.FxKeyCodeCombination.combination;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.fx.helpers.FxMenu.radioMenuitem;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.json.JSONException;

import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import sam.apps.jbook_reader.editor.Editor;
import sam.apps.jbook_reader.tabs.Tab;
import sam.apps.jbook_reader.tabs.TabContainer;
import sam.config.Session;
import sam.config.SessionPutGet;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.logging.MyLoggerFactory;
import sam.myutils.MyUtilsException;

public class App extends Application implements SessionPutGet {
	private static App INSTANCE;
	
	static {
		FxFxml.setFxmlDir(ClassLoader.getSystemResource("fxml"));
	}

	public static App getInstance() {
		return INSTANCE;
	}
	
	@FXML private BorderPane root;
	@FXML private SplitPane splitPane;

	private final ReadOnlyObjectWrapper<Tab> currentTab = new ReadOnlyObjectWrapper<>();
	private final BookmarksPane bookmarks = MyUtilsException.noError(() -> new BookmarksPane(currentTab));
	private final MultipleSelectionModel<TreeItem<String>> selectionModel = bookmarks.getSelectionModel();
	
	private final BooleanBinding currentTabNull = currentTab.isNull();
	private final Actions actions = Actions.getInstance();

	private final SimpleObjectProperty<File> currentFile = new SimpleObjectProperty<>();
	private final SimpleBooleanProperty searchActive = new SimpleBooleanProperty();
	private WeakReference<SearchBox> weakSearchBox = new WeakReference<SearchBox>(null);

	private final TabContainer tabsContainer = TabContainer.getInstance();
	private final Editor editor = Editor.getInstance(currentTab.getReadOnlyProperty(), selectionModel.selectedItemProperty());
	private static Stage stage;
	public static final ColorAdjust GRAYSCALE_EFFECT = new ColorAdjust();
	private BoundBooks boundBooks;
	
	static {
		GRAYSCALE_EFFECT.setSaturation(-1);
	}
	public static Stage getStage() {
		return stage;
	}
	@Override
	public void start(Stage stage) throws Exception {
		App.INSTANCE = this;
		App.stage = stage;
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);
		FileOpenerNE.setErrorHandler((file, error) -> FxAlert.showErrorDialog(file, "failed to open file", error));
		Session.put(Stage.class, stage);
		
		new FxFxml(this, stage, this)
		.putBuilder(BookmarksPane.class, bookmarks)
		.putBuilder(TabContainer.class, tabsContainer)
		.putBuilder(Editor.class, editor)
		.load();

		splitPane.setDividerPositions(0, 0.2);
		splitPane.widthProperty().addListener((p, o, n) -> splitPane.setDividerPosition(0, 0.2));
		prepareTabsContainer();
		root.setTop(getMenubar());

		loadIcon(stage);
		showStage(stage);
		readRecents();
		
		boundBooks = new BoundBooks();
		
		new FilesLookup().parse(getParameters().getRaw(), tabsContainer::addTab);
	}
	
	private void loadIcon(Stage stage) throws IOException {
		Path p = Paths.get("notebook.png");
		if(Files.exists(p))
			stage.getIcons().add(new Image(Files.newInputStream(p)));
	}
	private void readRecents() throws IOException, URISyntaxException {
		Path p = Utils.APP_DATA.resolve("recents.txt");
		if(Files.notExists(p))
			return;

		Files.lines(p)
		.map(String::trim)
		.filter(s -> !s.isEmpty())
		.map(File::new)
		.filter(File::exists)
		.distinct()
		.map(this::recentsMenuItem)
		.forEach(recentsMenu.getItems()::add);
	}
	private void showStage(Stage stage2) throws IOException {
		Rectangle2D screen = Screen.getPrimary().getBounds();

		BiFunction<String, Double, Double> get = (s,t) -> {
			try {
				return Double.parseDouble(sessionGetProperty(s));
			} catch (NullPointerException|NumberFormatException e) {}
			return t;
		};

		stage.setWidth(get.apply("stage.width", screen.getWidth()/2));
		stage.setHeight(get.apply("stage.height", screen.getHeight()));
		stage.setX(get.apply("stage.x", 0d));
		stage.setY(get.apply("stage.y", 0d));

		try {
			stage.getIcons().add(new Image("notebook.png"));
		} catch (Exception e2) {}

		stage.show();
		stage.setOnCloseRequest(e -> {
			exit();
			e.consume();
		});
	}
	private void prepareTabsContainer() {
		tabsContainer.setOnTabSwitch(this::switchTab);
		tabsContainer.setOnTabClosed(tab -> {
			File p = tab.getJbookPath();
			if(p == null)
				return;

			List<MenuItem> list = recentsMenu.getItems(); 

			list.add(0, recentsMenuItem(p));
			if(list.size() > 10)
				list.subList(10, list.size()).clear();
			actions.tabClosed(tab);
		});
	}
	private MenuItem recentsMenuItem(File path) {
		MenuItem mi =  menuitem(path.toString(), e -> actions.open(tabsContainer, path, recentsMenu));
		mi.getStyleClass().add("recent-mi");
		mi.setUserData(path);
		return mi;
	}
	
	private Tab getCurrentTab() {
		return currentTab.get();
	}
	
	private void exit() {
		boundBooks.save();
		if(tabsContainer.closeAll()) {
			
			sessionPut("stage.width", String.valueOf(stage.getWidth()));
			sessionPut("stage.height", String.valueOf(stage.getHeight()));
			sessionPut("stage.x", String.valueOf(stage.getX()));
			sessionPut("stage.y", String.valueOf(stage.getY()));

			try {
				Files.write(APP_DATA.resolve("recents.txt"), recentsMenu.getItems().stream()
						.map(MenuItem::getUserData)
						.map(Object::toString)
						.map(s -> s.replace('\\', '/'))
						.collect(Collectors.toList()), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				MyLoggerFactory.bySimpleName(getClass()).log(Level.SEVERE,  "failed to save: recents.txt  ", e);   
			}
			System.exit(0);
		}
	}
	private MenuBar getMenubar() throws JSONException, IOException {
		MenuBar bar =  new MenuBar(
				getFileMenu(), 
				bookmarks.getBookmarkMenu(), 
				getSearchMenu(), 
				getEditorMenu(),
				Optional.ofNullable(sessionGetProperty("debug"))
				.filter(s -> s.trim().equalsIgnoreCase("true"))
				.map(s -> getDebugMenu())
				.orElse(new Menu()));

		new DyanamiMenus().load(bar);
		return bar;
	}
	private Menu getDebugMenu() {
		return new Menu("debug", null,
				menuitem("no content bookmarks", e_e -> {
					String sb = getCurrentTab().walk()
							.filter(e -> e.getContent() == null || e.getContent().trim().isEmpty())
							.peek(e -> e.setExpanded(true))
							.reduce(new StringBuilder(), (sb2, t) -> Utils.treeToString(t, sb2), StringBuilder::append).toString();

					Clipboard cb = Clipboard.getSystemClipboard();
					Map<DataFormat, Object> map = new HashMap<>();
					map.put(DataFormat.PLAIN_TEXT, sb);
					cb.setContent(map);

					FxAlert.alertBuilder(AlertType.INFORMATION)
					.expandableText(sb)
					.expanded(true)
					.header("No Content Bookmarks")
					.showAndWait();
				}, currentTabNull)
				);
	}
	private Menu getEditorMenu() {
		Menu menu = new Menu("editor", null,
				radioMenuitem("Text wrap", e -> editor.setWordWrap(((RadioMenuItem)e.getSource()).isSelected())),
				menuitem("Font", e -> editor.setFont())
				);
		return menu;
	}
	private Menu getSearchMenu() {
		Menu menu = new Menu("_Search", null, 
				menuitem("Search", combination(KeyCode.F, SHORTCUT_DOWN), e -> {
					tabsContainer.setDisable(true);
					tabsContainer.setEffect(GRAYSCALE_EFFECT);
					searchActive.set(true);

					SearchBox sb = weakSearchBox.get();

					if(sb == null) {
						sb = new SearchBox(selectionModel, getCurrentTab());
						weakSearchBox = new WeakReference<>(sb);
						sb.setOnHidden(e_e -> {
							tabsContainer.setDisable(false);
							tabsContainer.setEffect(null);
							searchActive.set(false);
						});
					}
					else { 
						sb.start(getCurrentTab());
					}

				}, currentTabNull)
				);
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
				menuitem("_New", combination(KeyCode.N, SHORTCUT_DOWN, ALT_DOWN), e -> tabsContainer.addBlankTab(), searchActive),
				menuitem("_Open...", combination(KeyCode.O, SHORTCUT_DOWN), e -> actions.open(tabsContainer, null, recentsMenu), searchActive),
				recentsMenu,
				menuitem("Open Containing Folder", e -> actions.open_containing_folder(getHostServices(), getCurrentTab()), fileNull),
				menuitem("Reload From Disk", e -> actions.reload_from_disk(getCurrentTab()), fileNull),
				menuitem("_Save", combination(KeyCode.S, SHORTCUT_DOWN), e -> actions.save(getCurrentTab(), false), fileNull),
				menuitem("Save _As", combination(KeyCode.S, SHORTCUT_DOWN, SHIFT_DOWN), e -> {actions.save_as(getCurrentTab(), false);updateCurrentFile();}, tabNull),
				menuitem("Sav_e All", combination(KeyCode.S, SHORTCUT_DOWN, ALT_DOWN), e -> {tabsContainer.saveAllTabs();updateCurrentFile();}, tabNull),
				menuitem("Rename", e -> {actions.rename(getCurrentTab()); updateCurrentFile();}, fileNull),
				menuitem("Bind Book", e -> {boundBooks.bindBook(getCurrentTab());}, fileNull),
				new SeparatorMenuItem(),
				menuitem("_Close",combination(KeyCode.W, SHORTCUT_DOWN), e -> tabsContainer.closeTab(getCurrentTab()), selectedZero),
				menuitem("Close All",combination(KeyCode.W, SHORTCUT_DOWN, SHIFT_DOWN), e -> tabsContainer.closeAll(), selectedZero),
				closeSpecific,
				new SeparatorMenuItem(),
				menuitem("E_xit", combination(KeyCode.F4, ALT_DOWN), e -> exit())
				);
		closeSpecific.disableProperty().bind(tabsContainer.tabsCountProperty().lessThan(2).or(searchActive));
		return menu;
	}
	private void updateCurrentFile() {
		Tab t = getCurrentTab(); 
		if(t == null)
			return;

		currentFile.set(t.getJbookPath());
		stage.setTitle(t.getTabTitle());
	}
	private void switchTab(final Tab newTab) {
		currentTab.set(newTab);
		boolean b = newTab == null;
		stage.setTitle(b ? null : newTab.getTabTitle());
		currentFile.set(b ? null : newTab.getJbookPath());
		bookmarks.setRoot(b ? null : newTab.getRootItem());
		actions.switchTab(newTab);
	}

	public Editor editor() {
		return editor;
	}

}
