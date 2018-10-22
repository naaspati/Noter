package sam.noter.tabs;

import static sam.fx.helpers.FxClassHelper.addClass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import sam.fx.alert.FxAlert;
import sam.noter.ActionResult;
import sam.noter.BoundBooks;
import sam.noter.Utils;

public class TabContainer extends BorderPane {
	private final HBox tabsBox = new HBox(2);
	private final ReadOnlyIntegerWrapper tabsCount = new ReadOnlyIntegerWrapper();
	private final List<Tab> tabs = new ArrayList<>();
	private final ScrollPane sp = new ScrollPane(tabsBox);
	private ReadOnlyObjectWrapper<Tab> currentTab = new ReadOnlyObjectWrapper<>();
	private final Consumer<Tab> onSelect;

	private double div = 0;

	private static transient TabContainer instance;

	public static TabContainer getInstance() {
		if (instance == null) {
			synchronized (TabContainer.class) {
				if (instance == null)
					instance = new TabContainer();
			}
		}
		return instance;
	}

	private TabContainer() {
		setId("tab-container");
		onSelect = this::selectTab;
		
		addClass(tabsBox, "tab-box");

		tabsCount.bind(Bindings.size(tabsBox.getChildren()));
		tabsCount.addListener((p, o, n) -> div = 1/n.doubleValue());

		sp.getStyleClass().clear();
		sp.setHbarPolicy(ScrollBarPolicy.NEVER);
		sp.setVbarPolicy(ScrollBarPolicy.NEVER);

		Button left = new Button();
		Button right = new Button();
		addClass(right,"right");
		HBox rightLeft = new HBox(3, left, right);
		rightLeft.setId("scroll");

		right.disableProperty().bind(sp.hvalueProperty().isEqualTo(1));
		left.disableProperty().bind(sp.hvalueProperty().isEqualTo(0));

		left.setOnAction(e -> sp.setHvalue(sp.getHvalue() - div < 0 ? 0 : sp.getHvalue() - div));
		right.setOnAction(e -> sp.setHvalue(sp.getHvalue() + div > 1 ? 1 : sp.getHvalue() + div));

		setCenter(sp);
		setRight(rightLeft);

		rightLeft.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
			return getWidth() < tabsBox.getWidth() ;
		}, widthProperty(), tabsBox.widthProperty(), Bindings.size(tabsBox.getChildren())));


		Platform.runLater(() -> div = 1d/tabsBox.getChildren().size());
	}

	private final ContextMenu closeTabsContextMenu = new ContextMenu();
	{
		closeTabsContextMenu.getItems().addAll(
				menuitem("close other tab(s)", e -> closeExcept((Tab)closeTabsContextMenu.getUserData())),
				menuitem("close tab(s) to on right", e -> closeRightLeft((Tab)closeTabsContextMenu.getUserData(), true)),
				menuitem("close tab(s) to on left", e -> closeRightLeft((Tab)closeTabsContextMenu.getUserData(), false))
				);
	}

	private MenuItem menuitem(String string, EventHandler<ActionEvent> action) {
		MenuItem item = new MenuItem(string);
		item.setOnAction(action);
		return item;
	}

	public void addBlankTab() {
		Tab tab;
		try {
			tab = new Tab(onSelect);
		} catch (Exception e) {
			FxAlert.showErrorDialog(null, "failed to create Tab", e);
			return;
		}

		String title;
		int n = 1;
		loop:
			while(true) {
				title = "New "+(n++);
				for (Tab t : tabs) {
					if(title.equals(t.getTabTitle()))
						continue loop;
				}
				break;
			}
		tab.setTabTitle(title);
		addTab(tab);
	}
	public void addTab(File path) {
		if(path == null)
			return;
		try {
			addTab(new Tab(path, onSelect));
		} catch (Exception  e) {
			FxAlert.showErrorDialog(path, "failed to open file", e);
		}
	}
	
	private void addTab(Tab tab) {
		tabs.add(tab);
		tabsBox.getChildren().add(tab.getView());
		tab.setOnClose(this::closeTab);
		tab.setContextMenu(closeTabsContextMenu);
		selectTab(tab);
		File file = BoundBooks.getInstance().openBook(tab);
		tab.setBoundBook(file);
	}
	public void closeTab(Tab tab) {
		if(tab == null)
			return;

		if(tab.isModified()) {
			ActionResult ar = tab.save(true);
			if(ar != ActionResult.NO && ar != ActionResult.SUCCESS)
				return;
		} 

		tabs.remove(tab);
		int index = tabsBox.getChildren().indexOf(tab.getView());
		tabsBox.getChildren().remove(index);

		if(!tab.isActive())
			return;

		if(index < tabs.size())
			selectTab(tabs.get(index));
		else if(tabs.isEmpty())
			selectTab(null);
		else
			selectTab(tabs.get(tabs.size() - 1));
	}
	private void selectTab(Tab newTab) {
		tabs.forEach(t -> t.setActive(t == newTab));
		sp.setHvalue(newTab == null ? 0 : div*(tabsBox.getChildren().indexOf(newTab.getView()) - 1));
		currentTab.set(newTab);
	}
	public ReadOnlyIntegerProperty tabsCountProperty() {
		return tabsCount.getReadOnlyProperty();
	}
	public boolean closeAll() {
		new ArrayList<>(tabs).forEach(this::closeTab);
		return tabs.isEmpty();
	}
	public void closeExcept(Tab currentTab) {
		if(currentTab == null)
			return;

		new ArrayList<>(tabs).stream()
		.filter(t -> t != currentTab)
		.forEach(this::closeTab);
	}
	public void closeRightLeft(Tab currentTab, boolean rightSide) {
		if(currentTab == null || tabs.size() < 2)
			return;
		int index = tabs.indexOf(currentTab);
		List<Tab> list;
		if(rightSide) {
			if(index == tabs.size() - 1)
				return;
			list = new ArrayList<>(tabs.subList(index + 1, tabs.size()));
		}
		else {
			if(index == 0)
				return;
			list = new ArrayList<>(tabs.subList(0, index));
		}
		list.forEach(this::closeTab);
	}

	public void saveAllTabs() {
		for (Tab tab : tabs)
			tab.save(false);
	}
	public List<File> getJbookPaths() {
		return tabs.stream().map(Tab::getJbookPath)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	public void forEach(Consumer<Tab> action) {
		tabs.forEach(action);
	}

	public void open(File jbookPath, Menu recentsMenu)  {
		if(jbookPath == null) {
			File file = Utils.getFile("select a file to open...", null);

			if(file == null)
				return;

			jbookPath = file;
		}
		addTab(jbookPath);

		File p = jbookPath;

		recentsMenu.getItems()
		.removeIf(mi -> p.equals(mi.getUserData()));
	}

	public ReadOnlyObjectProperty<Tab> tabProperty() {
		return currentTab.getReadOnlyProperty();
	}
}
