package sam.noter;

import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.fx.helpers.FxKeyCodeCombination.combination;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.fx.helpers.FxMenu.radioMenuitem;
import static sam.noter.Utils.APP_DATA;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import sam.config.Session;
import sam.config.SessionHelper;
import sam.fx.alert.FxAlert;
import sam.fx.clipboard.FxClipboard;
import sam.fx.helpers.FxFxml;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.logging.MyLoggerFactory;
import sam.noter.bookmark.BookmarksPane;
import sam.noter.bookmark.SearchBox;
import sam.noter.editor.Editor;
import sam.noter.tabs.Tab;
import sam.noter.tabs.TabContainer;

public class App extends Application implements SessionHelper, ChangeListener<Tab> {
	static {
		FxFxml.setFxmlDir(ClassLoader.getSystemResource("fxml"));
	}

	@FXML private BorderPane root;
	@FXML private SplitPane splitPane;

	private TabContainer tabsContainer;
	private ReadOnlyObjectProperty<Tab> currentTab;
	private BookmarksPane bookmarks;

	private BooleanBinding currentTabNull;

	private final SimpleObjectProperty<File> currentFile = new SimpleObjectProperty<>();
	private final SimpleBooleanProperty searchActive = new SimpleBooleanProperty();
	private WeakReference<SearchBox> weakSearchBox = new WeakReference<SearchBox>(null);

	private Editor editor;
	public static final ColorAdjust GRAYSCALE_EFFECT = new ColorAdjust();
	private BoundBooks boundBooks;
	private Stage stage;

	static {
		GRAYSCALE_EFFECT.setSaturation(-1);
	}
	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		FxAlert.setParent(stage);
		FxPopupShop.setParent(stage);
		FileOpenerNE.setErrorHandler((file, error) -> FxAlert.showErrorDialog(file, "failed to open file", error));
		Session.put(Stage.class, stage);

		boundBooks = new BoundBooks();

		tabsContainer = new TabContainer(boundBooks);
		currentTab = tabsContainer.currentTabProperty();
		currentTabNull = currentTab.isNull();

		editor = new Editor();
		bookmarks = new BookmarksPane(editor, tabsContainer);
		editor.init(bookmarks.selectedItemProperty(), tabsContainer);

		new FxFxml(this, stage, this)
		.putBuilder(BookmarksPane.class, bookmarks)
		.putBuilder(TabContainer.class, tabsContainer)
		.putBuilder(Editor.class, editor)
		.load();

		splitPane.setDividerPositions(0, 0.2);
		splitPane.widthProperty().addListener((p, o, n) -> splitPane.setDividerPosition(0, 0.2));
		currentTab.addListener(this);
		root.setTop(getMenubar());

		stage.getScene().getStylesheets().add("css/style.css");
		loadIcon(stage);
		showStage(stage);
		readRecents();

		List<File> files  = new FilesLookup().parse(getParameters().getRaw());
		tabsContainer.addTabs(files);
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
	private MenuItem recentsMenuItem(File path) {
		MenuItem mi =  menuitem(path.toString(), e -> tabsContainer.open(Collections.singletonList(path), recentsMenu));
		mi.getStyleClass().add("recent-mi");
		mi.setUserData(path);
		return mi;
	}

	private Tab getCurrentTab() {
		return currentTab.get();
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		Utils.stop();
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
			Platform.exit();
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

		new DyanamiMenus().load(bar, editor);
		return bar;
	}
	private Menu getDebugMenu() {
		return new Menu("debug", null,
				menuitem("no content bookmarks", e_e -> {
					StringBuilder sb = new StringBuilder();
					getCurrentTab().walk(e -> {
						if(e.getContent() == null || e.getContent().trim().isEmpty()) {
							e.setExpanded(true);
							sb.append(e.toTreeString());							
						}
					});
					FxClipboard.copyToClipboard(sb.toString());

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
						sb = new SearchBox(stage, bookmarks, getCurrentTab());
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
				menuitem("_Open...", combination(KeyCode.O, SHORTCUT_DOWN), e -> tabsContainer.open(null, recentsMenu), searchActive),
				recentsMenu,
				menuitem("Open Containing Folder", e -> getCurrentTab().open_containing_folder(getHostServices()), fileNull),
				menuitem("Reload From Disk", e -> getCurrentTab().reload_from_disk(), fileNull),
				menuitem("_Save", combination(KeyCode.S, SHORTCUT_DOWN), e -> getCurrentTab().save(false), fileNull),
				menuitem("Save _As", combination(KeyCode.S, SHORTCUT_DOWN, SHIFT_DOWN), e -> {getCurrentTab().save_as(false);updateCurrentFile();}, tabNull),
				menuitem("Sav_e All", combination(KeyCode.S, SHORTCUT_DOWN, ALT_DOWN), e -> {tabsContainer.saveAllTabs();updateCurrentFile();}, tabNull),
				menuitem("Rename", e -> {getCurrentTab().rename(); updateCurrentFile();}, fileNull),
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
		File p = oldValue.getJbookPath();
		if(p == null)
			return;

		List<MenuItem> list = recentsMenu.getItems(); 

		list.add(0, recentsMenuItem(p));
		if(list.size() > 10)
			list.subList(10, list.size()).clear();
	}

}
