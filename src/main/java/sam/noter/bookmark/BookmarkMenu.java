package sam.noter.bookmark;

import static javafx.scene.input.KeyCombination.ALT_DOWN;
import static javafx.scene.input.KeyCombination.SHIFT_DOWN;
import static javafx.scene.input.KeyCombination.SHORTCUT_DOWN;
import static sam.fx.helpers.FxKeyCodeUtils.combination;
import static sam.fx.helpers.FxMenu.menuitem;
import static sam.noter.bookmark.BookmarkType.CHILD;
import static sam.noter.bookmark.BookmarkType.RELATIVE;
import static sam.noter.bookmark.BookmarkType.RELATIVE_TO_PARENT;

import javafx.beans.binding.BooleanBinding;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Menu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import sam.di.Injector;
import sam.nopkg.EnsureSingleton;
import sam.noter.api.MenusProvider;

public class BookmarkMenu implements MenusProvider {
    private static final EnsureSingleton singleton = new EnsureSingleton();
    {   singleton.init(); }

    @Override
    public Menu create() {
        singleton.init();
        
        Injector in = Injector.getInstance();
       BookmarksPane bookmarks = in.instance(BookmarksPane.class);
        BooleanBinding selectedItemNull = bookmarks.selectedEntryProperty().isNull();
        
        return new Menu("_Bookmark",
                null,
                menuitem("Add Bookmark", combination(KeyCode.N, SHORTCUT_DOWN), e -> bookmarks.addNewBookmark(RELATIVE), selectedItemNull),
                menuitem("Add Child Bookmark", combination(KeyCode.N, SHORTCUT_DOWN, SHIFT_DOWN), e -> bookmarks.addNewBookmark(CHILD), selectedItemNull),
                menuitem("Add Bookmark Relative to Parent", combination(KeyCode.N, ALT_DOWN, SHIFT_DOWN), e -> bookmarks.addNewBookmark(RELATIVE_TO_PARENT), selectedItemNull),
                new SeparatorMenuItem(),
                menuitem("Remove bookmark", this::removeAction, selectedItemNull),
                //FIXME menuitem("Undo Removed bookmark", e -> remover().undoRemoveBookmark(tab), undoDeleteSize.isEqualTo(0)),
                new SeparatorMenuItem()
                //FIXME , menuitem("Move bookmark", e -> mover().moveBookmarks(tree.model()), selectedItemNull)
                );
    }
    @FXML
    private void removeAction(ActionEvent e) {
        //FIXME remover().removeAction(tree.model(), tab);
    }

}
