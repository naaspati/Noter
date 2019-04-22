package sam.noter.app;
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
import static sam.noter.Utils.fx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Named;

import org.codejargon.feather.Provides;
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
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import sam.di.FeatherInjector;
import sam.di.Injector;
import sam.fx.alert.FxAlert;
import sam.fx.helpers.FxFxml;
import sam.fx.helpers.FxStageState;
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.DirWatcher;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.Checker;
import sam.myutils.MyUtilsPath;
import sam.nopkg.EnsureSingleton;
import sam.noter.BoundBooks;
import sam.noter.Utils;
import sam.noter.api.Configs;
import sam.noter.api.FileChooser2;
import sam.noter.api.MenusProvider;
import sam.noter.api.OnExitQueue;
import sam.noter.bookmark.BookmarksPane;
import sam.noter.dao.api.IRootEntry;
import sam.noter.editor.Editor;
import sam.noter.tabs.TabBox;
import sam.reference.WeakAndLazy;
import sam.thread.MyUtilsThread;
public class App extends Application implements ChangeListener<IRootEntry>, FileChooser2Impl {
    private static final EnsureSingleton singleons = new EnsureSingleton();
    { singleons.init(); }


    public static final String CONFIG_PATH = "CONFIG_PATH";
    public static final String MAIN_STAGE = "main-stage";
    
    private final Logger logger = Utils.logger(App.class);

    static {
        FxFxml.setFxmlDir(ClassLoader.getSystemResource("fxml"));
    }

    public static final ColorAdjust GRAYSCALE_EFFECT = new ColorAdjust();

    private final BorderPane main_view = new BorderPane();
    private final StackPane main_stacks = new StackPane(main_view);
    private final Scene main_scene = new Scene(main_stacks);

    private BookmarksPane bookmarks;
    private TabBox tabsBox;
    private Editor editor;
    private BoundBooks bound_books;
    
    private FxStageState stageState;

    private final Path selfDir = MyUtilsPath.selfDir();
    private Configs configs;
    private Actions actions;
    private final OnExitQueueImpl exitQueue = new OnExitQueueImpl();

    @Override
    public void init() throws Exception {
        Injector.init(new FeatherInjector(this));
        Injector injector = Injector.getInstance();
        configs = injector.instance(Configs.class);
        
        GRAYSCALE_EFFECT.setSaturation(-1);
    }

    private final SimpleObjectProperty<Path> currentFile = new SimpleObjectProperty<>();
    private final SimpleBooleanProperty searchActive = new SimpleBooleanProperty();

    private Stage stage;
    private IRootEntry currentRoot;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        FxAlert.setParent(stage);
        FxPopupShop.setParent(stage);
        FileOpenerNE.setErrorHandler((file, error) -> FxAlert.showErrorDialog(file, "failed to open file", error));

        Injector injector = Injector.getInstance();
        this.bookmarks = injector.instance(BookmarksPane.class);
        this.tabsBox = injector.instance(TabBox.class);
        this.editor = injector.instance(Editor.class);
        this.actions = injector.instance(Actions.class);

        tabsBox.selectedRootProperty()
        .addListener(this);

        BorderPane right = new BorderPane(editor);
        right.setTop(tabsBox);
        SplitPane splitPane = new SplitPane(bookmarks, right);

        main_view.setCenter(splitPane);
        main_view.setTop(getMenubar());

        splitPane.setDividerPositions(0, 0.2);
        splitPane.widthProperty().addListener((p, o, n) -> splitPane.setDividerPosition(0, 0.2));

        loadIcon(stage);
        stage.setScene(main_scene);
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
                List<Path> files = fileLookup().parse(input);
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

                actions.addTabs(files);
                stage.toFront();

            } catch (IOException e) {
                FxAlert.showErrorDialog(String.join("\n", input), "failed to read", e);
            }
        });
    }

    private FilesLookup fileLookup() {
        return Injector.getInstance().instance(FilesLookup.class);
    }

    private void loadIcon(Stage stage) throws IOException {
        Path p = Paths.get("notebook.png");
        if(Files.exists(p))
            stage.getIcons().add(new Image(Files.newInputStream(p)));
    }
    private void showStage(Stage stage2) throws IOException {
        stageState = new FxStageState(configs.appDataDir().resolve("stage.settings")) {
            @Override
            protected void defaultApply(Stage stage) {
                Rectangle2D screen = Screen.getPrimary().getBounds();

                stage.setWidth(screen.getWidth()/2);
                stage.setHeight(screen.getHeight());
                stage.setX(0d);
                stage.setY(0d);
            }
        };

        stageState.apply(stage);

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
        synchronized (exitQueue) {
            if(!exitQueue.list.isEmpty())
                exitQueue.list.forEach(Runnable::run);    
        }

        if(actions.closeAllTabs()) {
            try {
                stageState.save(stage);
            } catch (IOException e) {
                logger.error("failed to write: {}", stageState.path, e);
            }
            Platform.exit();
        }
    }
    private MenuBar getMenubar() throws JSONException, IOException {
        MenuBar bar =  new MenuBar( getFileMenu() );
        
        ServiceLoader.load(MenusProvider.class)
        .forEach(e -> bar.getMenus().add(e.create()));
        
        return bar;
    }

    private Menu getFileMenu() {
        BooleanBinding fileNull = currentFile.isNull().or(searchActive);
        BooleanBinding tabNull = tabsBox.selectedRootIsNull().or(searchActive);
        BooleanBinding selectedZero = tabsBox.tabsCountProperty().isEqualTo(0).or(searchActive);

        Menu closeSpecific = 
                new Menu("Close specific", null,
                        menuitem("other tab(s)", e -> actions.closeTabsExcept(currentRoot)),
                        menuitem("tab(s) to the right", e -> actions.closeTabsRight(currentRoot)),
                        menuitem("tab(s) to the left", e -> actions.closeTabsLeft(currentRoot))
                        );

        Menu menu = new Menu("_File");
        Menu recentsMenu = new RecentMenu().create();
        recentsMenu.disableProperty().bind(searchActive.or(Bindings.isEmpty(recentsMenu.getItems())));

        new Menu("_File", null, 
                menuitem("_New", combination(N, SHORTCUT_DOWN, ALT_DOWN), e -> actions.addBlankTab(), searchActive),
                menuitem("_Open...", combination(O, SHORTCUT_DOWN), e -> actions.openChoosenFileTab(), searchActive),
                recentsMenu,
                menuitem("Open Containing Folder", e -> actions.open_containing_folder(currentRoot), fileNull),
                menuitem("Reload From Disk", e -> actions.reload_from_disk(currentRoot), fileNull),
                menuitem("_Save", combination(S, SHORTCUT_DOWN), e -> actions.save(currentRoot, false), fileNull),
                menuitem("Save _As", combination(S, SHORTCUT_DOWN, SHIFT_DOWN), e -> {actions.save_as(currentRoot, false);updateCurrentFile();}, tabNull),
                menuitem("Sav_e All", combination(S, SHORTCUT_DOWN, ALT_DOWN), e -> {actions.saveAllTabs();updateCurrentFile();}, tabNull),
                menuitem("Rename", e -> {actions.rename(currentRoot); updateCurrentFile();}, fileNull),
                menuitem("Bind Book", e -> bind(), fileNull),
                new SeparatorMenuItem(),
                menuitem("_Close",combination(W, SHORTCUT_DOWN), e -> actions.closeTab(currentRoot), selectedZero),
                menuitem("Close All",combination(W, SHORTCUT_DOWN, SHIFT_DOWN), e -> actions.closeAllTabs(), selectedZero),
                closeSpecific,
                new SeparatorMenuItem(),
                menuitem("E_xit", combination(F4, ALT_DOWN), e -> exit())
                );

        if(!config().getBoolean("OPEN_CMD_ENABLE"))
            menu.getItems().add(2,  menuitem("Open By Cmd", combination(O, SHORTCUT_DOWN, SHIFT_DOWN), e -> openByCmd(), searchActive));

        closeSpecific.disableProperty().bind(tabsBox.tabsCountProperty().lessThan(2).or(searchActive));
        return menu;
    }

    private void bind() {
        if(bound_books == null)
            bound_books = Injector.getInstance().instance(BoundBooks.class);

        bound_books.bindBook(currentRoot);
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

    @Provides
    public Actions actions() {
        return actions;
    }
    @Override
    public void changed(ObservableValue<? extends IRootEntry> observable, IRootEntry oldValue, IRootEntry newValue) {
        boolean b = newValue == null;
        currentRoot = newValue;

        setTitle(newValue);
        currentFile.set(b ? null : newValue.getJbookPath());
    }
    
    @Provides
    @Override 
    public Configs config() { 
        return configs; 
    }

    private final WeakAndLazy<FileChooser> fchooser = new WeakAndLazy<>(FileChooser::new);
    @Override 
    public FileChooser newFileChooser() { 
        return fchooser.get();
    }

    @Provides 
    public FileChooser2 fc2() { 
        return this;
    }
    
    @Provides 
    @Named(MAIN_STAGE)
    public Stage stage() { 
        return stage;
    }
    
    @Provides 
    @Named(CONFIG_PATH)
    public Path config_path() { 
        return selfDir.resolve("app_config.properties");
    }
    @Provides 
    public OnExitQueue exiq() { 
        return exitQueue;
    }

    class OnExitQueueImpl implements OnExitQueue {
        private final ArrayList<Runnable> list = new ArrayList<>();

        @Override
        public synchronized void runOnExist(Runnable runnable) {
            list.add(Objects.requireNonNull(runnable));
        }

    }
}
