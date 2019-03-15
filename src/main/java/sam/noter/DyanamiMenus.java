package sam.noter;

import static sam.noter.EnvKeys.DYNAMIC_MENUS_FILE;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCombination;
import sam.fx.alert.FxAlert;
import sam.myutils.System2;
import sam.noter.editor.Editor;
public class DyanamiMenus {
	private final Logger logger = Utils.logger(DyanamiMenus.class);
	private MenuBar bar;
	private Editor editor;

	public void load(MenuBar bar, Editor editor) throws JSONException, IOException {
		this.bar = bar;
		this.editor = editor;
		String s = System2.lookup(DYNAMIC_MENUS_FILE);
		if(s == null) {
			logger.warn("{} variable not set", DYNAMIC_MENUS_FILE);
			return;
		}

		Path p = Paths.get(s);
		if(Files.notExists(p)) {
			logger.error("{} not found: {}", DYNAMIC_MENUS_FILE, s);
			return;
		}
		JSONObject json = new JSONObject(Files.lines(p).collect(Collectors.joining()));
		json.toMap().forEach(this::configMenu);
	}

	@SuppressWarnings("unchecked")
	private void configMenu(String key, Object data) {
		Menu menu = bar.getMenus().stream().filter(m -> {
			String s2 = m.getText();
			if(s2.equalsIgnoreCase(key))
				return true;
			if(s2.charAt(0) == '_')
				s2 = s2.substring(1);

			return s2.equalsIgnoreCase(key);
		}).findFirst().orElse(null);

		if(menu == null) {
			logger.error("no menu found with name: {}", key);
			return;
		}

		((Map<String, Map<String, Object>>)data).forEach((s,t) -> menu.getItems().add(newMI(s, t)));
	} 

	private MenuItem newMI(String name, Map<String, Object> config) {
		MenuItem mi = new MenuItem(name);

		config.forEach((s,t) -> {
			if(t == null) return;

			switch (s.toLowerCase()) {
				case "onmenuvalidation":  loadClass((String)t, (EventHandler<Event> e) -> mi.setOnMenuValidation(e)); break;
				case "id":  mi.setId((String)t); break;
				case "style":  mi.setStyle((String)t); break;
				case "text":  mi.setText((String)t); break;
				case "consume":  consume(mi, (String)t); break;
				case "onaction":  loadClass((String)t, (EventHandler<ActionEvent> e) -> mi.setOnAction(e)); break;
				case "disable":  mi.setDisable((boolean)t); break;
				case "visible":  mi.setVisible((boolean)t); break;
				case "accelerator":  mi.setAccelerator(KeyCombination.valueOf((String)t)); break;
				case "mnemonicparsing":  mi.setMnemonicParsing((boolean)t); break;
			}
		});

		return mi;
	}

	@SuppressWarnings("unchecked")
	private void consume(MenuItem mi, String clsName) {
		if(clsName == null) return ;
		mi.setOnAction(e -> {
			try {
				Consumer<TextArea> c = (Consumer<TextArea>) Class.forName(clsName).newInstance();
				WeakReference<Consumer<TextArea>> w = new WeakReference<>(c);
				mi.setOnAction(e1 -> consume2(mi, clsName, w));
				consume2(mi, clsName, w);
			} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | ClassCastException e1) {
				FxAlert.showErrorDialog(null, "failed to load class: "+clsName, e1);
				logger.error( "failed to load class: "+clsName, e1);
				mi.setDisable(true);
			}
		});
	}
	private void consume2(MenuItem mi, String clsName, WeakReference<Consumer<TextArea>> weak) {
		Consumer<TextArea> c = weak.get();
		if(c == null) { 
			logger.debug(clsName+": gabaged");
			consume(mi, clsName);
		} else {
			editor.consume(c);			
		}
	}
	@SuppressWarnings("unchecked")
	private <E> void loadClass(String clsName, Consumer<E> consumer) {
		try {
			E e = (E)Class.forName(clsName).newInstance();
			consumer.accept(e);
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			logger.error( "failed to load class: "+clsName, e); 	
		}
	}
}
