package sam.noter.app;

import static sam.fx.helpers.FxMenu.menuitem;

import java.nio.file.Path;
import java.util.List;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import sam.di.Injector;
import sam.noter.api.MenusProvider;
import sam.noter.dao.RootEntryFactory;
import sam.noter.dao.api.IRootEntry;
import sam.noter.tabs.TabBox;

class RecentMenu implements MenusProvider, EventHandler<ActionEvent> {
    
    private Menu menu;
    private Actions actions;

    @Override
    public Menu create() {
        menu =  new Menu("_Recents", null, new MenuItem()); // first menuitem is needed to file onshowing event
        TabBox tabsBox = Injector.getInstance().instance(TabBox.class);
        this.actions = Injector.getInstance().instance(Actions.class);

        menu.setOnShowing(e -> {
            menu.setOnShowing(null);
            loadRecents();
        });

        tabsBox.addListener(new ListChangeListener<IRootEntry>() {

            @Override
            public void onChanged(Change<? extends IRootEntry> c) {
                while(c.next()) {
                    if(c.wasAdded()) {
                        for (IRootEntry t : c.getAddedSubList()) {
                            if(t != null && t.getJbookPath() != null)
                                remove(t.getJbookPath());   
                        }
                    } else if(c.wasRemoved()) {
                        for (IRootEntry t : c.getRemoved()) {
                            if(t != null && t.getJbookPath() != null)
                                add(t.getJbookPath(), 0);   
                        }
                    }   
                }
            }
        });

        return menu;
    }


    @Override
    public void handle(ActionEvent e) {
        MenuItem m = (MenuItem) e.getSource();
        actions.openTab((Path)m.getUserData());
        remove(m);
    }
    private void add(Path p, int index) {
        MenuItem mi =  menuitem(p.toString(), this);
        mi.getStyleClass().add("recent-mi");
        mi.setUserData(p);

        if(index < 0)
            menu.getItems().add(mi);
        else
            menu.getItems().add(index, mi);

    }
    private void clear(MenuItem m) {
        m.setOnAction(null);
        m.setUserData(null);
    }
    private void remove(MenuItem m) {
        clear(m);
        menu.getItems().remove(m);
    }
    private void remove(Path p) {
        menu.getItems().removeIf(m -> {
            if(m.getUserData().equals(p)) {
                clear(m);
                return true;
            } else 
                return false;
        });
    }

    void loadRecents() {
        List<Path> list = Injector.getInstance().instance(RootEntryFactory.class).recentsFiles(); 

        menu.getItems().clear();     
        list.forEach(t -> add(t, -1));
    }
}
