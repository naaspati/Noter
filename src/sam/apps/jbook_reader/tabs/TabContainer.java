package sam.apps.jbook_reader.tabs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import sam.apps.jbook_reader.Actions;
import sam.apps.jbook_reader.Actions.ActionResult;
import sam.fx.alert.FxAlert;

public class TabContainer extends BorderPane {
	private final HBox tabsBox = new HBox(2);
	private final ReadOnlyIntegerWrapper tabsCount = new ReadOnlyIntegerWrapper();
	private final List<Tab> tabs = new ArrayList<>();
	private final ScrollPane sp = new ScrollPane(tabsBox); 
	private Consumer<Tab> onTabSwitch, onSelect, onTabClosing;

	private double div = 0;

	public TabContainer() {
		tabsBox.getStyleClass().add("tabs-box");
		tabsCount.bind(Bindings.size(tabsBox.getChildren()));
		tabsCount.addListener((p, o, n) -> div = 1/n.doubleValue());

		onSelect = this::selectTab;

		sp.getStyleClass().clear();
		sp.setHbarPolicy(ScrollBarPolicy.NEVER);
		sp.setVbarPolicy(ScrollBarPolicy.NEVER);

		Button left = new Button();
		Button right = new Button();
		right.getStyleClass().add("button-right");
		HBox rightLeft = new HBox(3, left, right);
		rightLeft.getStyleClass().add("tabs-box-scroll");

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
		Tab tab = new Tab(onSelect);

		String title;
		int n = 1;
		loop:
			while(true) {
				title = "New "+(n++);
				for (Tab t : tabs) {
					if(title.equals(t.getTitle()))
						continue loop;
				}
				break;
			}
		tab.setTitle(title);
		addTab(tab);
	}
	public void addTab(Path path) {
		if(path == null)
			return;
		try {
			addTab(new Tab(path, onSelect));
		} catch (Exception  e) {
			FxAlert.showErrorDialoag(path, "failed to open file", e);
		}
	}
	private void addTab(Tab tab) {
		tabs.add(tab);
		tabsBox.getChildren().add(tab.getView());
		tab.setOnClose(this::closeTab);
		tab.setContextMenu(closeTabsContextMenu);
		selectTab(tab);
	}
	public void closeTab(Tab tab) {
		if(tab == null)
			return;

		onTabClosing.accept(tab);

		if(tab.isModified() && Actions.save(tab, true) == ActionResult.FAILED) 
			return;

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
		onTabSwitch.accept(newTab);
	}
	public void setOnTabSwitch(Consumer<Tab> consumer) {
		onTabSwitch = consumer;
	}
	public void setOnTabClosing(Consumer<Tab> consumer) {
		onTabClosing = consumer;
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
			Actions.save(tab, false);
	}
}
