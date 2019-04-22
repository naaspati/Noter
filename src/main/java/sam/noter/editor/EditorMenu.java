package sam.noter.editor;

import static sam.fx.helpers.FxMenu.menuitem;
import static sam.fx.helpers.FxMenu.radioMenuitem;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import sam.di.Injector;
import sam.fx.helpers.FxBindings;
import sam.fx.helpers.FxHBox;
import sam.fx.popup.FxPopupShop;
import sam.nopkg.EnsureSingleton;
import sam.noter.EntryTreeItem;
import sam.noter.Utils;
import sam.noter.api.FileChooser2;
import sam.noter.api.MenusProvider;
import sam.noter.app.App;
import sam.noter.bookmark.BookmarksPane;
import sam.noter.dao.api.IRootEntry;
import sam.noter.tabs.TabBox;
import sam.reference.WeakAndLazy;

public class EditorMenu implements MenusProvider {
    private static final EnsureSingleton singleton = new EnsureSingleton();
    {   singleton.init(); }
    
    private Injector injector;

    @Override
    public Menu create() {
        singleton.init();
        
        injector = Injector.getInstance();
        
        Editor editor = injector.instance(Editor.class);
        TabBox box = injector.instance(TabBox.class);
        
        Menu menu = new Menu("editor", null,
                menuitem("copy EntryTreeItem Tree", e -> Utils.copyToClipboard(Utils.toTreeString(editor.currentItem(), true)), editor.currentItemProperty().isNull()),
                radioMenuitem("Text wrap", e -> editor.setWordWrap(((RadioMenuItem)e.getSource()).isSelected())),
                menuitem("combine everything", this::combineEverything, box.selectedRootIsNull())
                );
        
        return menu;
    }
    
    private class CombinedAll {
        private final WeakAndLazy<StringBuilder> sb = new WeakAndLazy<>(StringBuilder::new);
        private final Hyperlink link = new Hyperlink("<< BACK");
        private final Text content = new Text();
        private final Text scrollPercent = new Text();
        private final Text lines = new Text();
        private final Scene myScene;

        private Scene previous;
        private IRootEntry tab;
        private int linesCount;
        private Stage stage;
        private BookmarksPane bookmarks;
        private TabBox tabs;

        public CombinedAll() {
            link.setOnAction(e1 -> stop());

            ScrollPane sp = new ScrollPane(content);
            sp.setPadding(new Insets(0, 0, 0, 5));
            sp.setStyle("-fx-background:white");

            Button save = new Button("save");
            save.setOnAction(e1 -> save());

            scrollPercent.textProperty().bind(FxBindings.map(sp.vvalueProperty(), s -> String.valueOf((int)(s.doubleValue()*100))));
            HBox box = new HBox(10,link, FxHBox.maxPane(), save, lines, scrollPercent, new Text());
            box.setAlignment(Pos.CENTER_RIGHT);
            box.setId("combined-all-top");

            BorderPane root = new BorderPane(sp, box, null, null, null);
            this.myScene  = new Scene(root, Color.WHITE);
            root.setId("combined-all");
        }

        private void save() {
            File file = injector.instance(FileChooser2.class).chooseFile("save in text", null, tab.getJbookPath().getFileName()+ ".txt", FileChooser2.Type.SAVE, null);
            if(file == null) {
                FxPopupShop.showHidePopup("cancelled", 1500);
                return;
            } 
            Utils.writeTextHandled(content.getText(), file.toPath());
        }

        public void stop() {
            stage.hide();
            stage.setScene(previous);
            stage.show();
            previous = null;
            tab = null;
            this.stage = null;
            this.tabs = null;
            this.bookmarks = null;
        }

        public void start() {
            this.stage = injector.instance(Stage.class, App.MAIN_STAGE);
            this.tabs = injector.instance(TabBox.class);
            this.bookmarks = injector.instance(BookmarksPane.class);
            
            this.previous = stage.getScene();
            this.tab = tabs.selectedRoot();

            StringBuilder sb = this.sb.get();
            sb.setLength(0);
            separator = new char[0];
            linesCount = 0;
            walk(bookmarks.getRoot().getChildren(), sb, "");

            content.setText(sb.toString());
            lines.setText("lines: "+linesCount+", chars: "+sb.length()+", scroll:");

            separator = null;
            sb = null;
            stage.hide();
            stage.setScene(myScene);
            stage.show();
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

                String s = e.getContent();
                if(s != null) {
                    for (int i = 0; i < s.length(); i++)
                        if(s.charAt(i) == '\n')
                            linesCount++;
                }
                sb.append(s);
                sb.append('\n').append('\n');

                List<TreeItem<String>> list = t.getChildren();
                if(!list.isEmpty())
                    walk(list, sb, tag.concat(" > "));
            }
        }
    }

    private final WeakAndLazy<CombinedAll> combinedAll = new WeakAndLazy<>(CombinedAll::new);
    private void combineEverything(ActionEvent e) {
        combinedAll.get().start();
    }

}
