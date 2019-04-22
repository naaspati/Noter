package sam.noter.app;

import static sam.fx.helpers.FxMenu.menuitem;

import java.lang.management.ManagementFactory;

import javafx.scene.control.Menu;
import javafx.scene.control.TextArea;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import sam.di.Injector;
import sam.fx.alert.FxAlert;
import sam.nopkg.Junk;
import sam.noter.api.MenusProvider;

public class InfoMenu implements MenusProvider {
    
    @Override
    public Menu create() {
        return new Menu("info", null,
                menuitem("ProgramName", e -> FxAlert.showMessageDialog("ManagementFactory.getRuntimeMXBean().getName()", ManagementFactory.getRuntimeMXBean().getName())),
                menuitem("Memory usage", e -> {
                    FxAlert.alertBuilder(AlertType.INFORMATION)
                    .content(new TextArea(Junk.memoryUsage()))
                    .header("Memory Usage")
                    .owner(Injector.getInstance().instance(Stage.class, App.MAIN_STAGE))
                    .show();
                })
                );
    }

}
