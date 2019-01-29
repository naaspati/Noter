package sam.noter.tabs;

import static sam.fx.helpers.FxClassHelper.addClass;
import static sam.noter.Utils.chooseFile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import sam.fx.popup.FxPopupShop;
import sam.io.fileutils.FileOpenerNE;
import sam.myutils.Checker;
import sam.noter.ActionResult;
import sam.noter.BoundBooks;
import sam.noter.Utils;
import sam.noter.Utils.FileChooserType;

public class TabContainer extends BorderPane implements ChangeListener<Tab> {
	private final HBox tabsBox = new HBox(2);
	private final ReadOnlyIntegerWrapper tabsCount = new ReadOnlyIntegerWrapper();
	private final List<Tab> tabs = new ArrayList<>();
	private final ScrollPane sp = new ScrollPane(tabsBox);
	private ReadOnlyObjectWrapper<Tab> currentTab = new ReadOnlyObjectWrapper<>();
	private final Consumer<Tab> onSelect;
	private final BoundBooks boundBooks;
	private final ArrayList<Consumer<Tab>> tabclosing = new ArrayList<>(); 

	private double div = 0;

	public TabContainer(BoundBooks boundBooks) {
		setId("tab-container");
		onSelect = currentTab::set;
		currentTab.addListener(this);
		this.boundBooks = boundBooks;

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
	
	public boolean removeOnTabClosing(Consumer<Tab> action) {
		return tabclosing.remove(action);
	}
	public void addOnTabClosing(Consumer<Tab> action) {
		tabclosing.add(action);
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
			
			Pattern pattern = Pattern.compile("New (\\d+)");
			
			int n = tabs.stream()
			.map(Tab::getTabTitle)
			.map(pattern::matcher)
			.filter(Matcher::find)
			.map(m -> m.group(1))
			.mapToInt(Integer::parseInt)
			.max()
			.orElse(0);
			
			String s = "New "+(n+1);
			File p = Utils.chooseFile("create new file", null, s, FileChooserType.SAVE);
			if(p == null) {
				FxPopupShop.showHidePopup("cancelled", 1500);
				return;
			}
			tab = Tab.create(p.toPath(), onSelect);
			addTab(tab, true);
		} catch (Exception e) {
			FxAlert.showErrorDialog(null, "failed to create Tab", e);
			return;
		}
	}

	public void addTabs(List<Path> files) {
		if(Checker.isEmpty(files))
			return;

		int index = tabs.size();
		for (Path file : files) {
			try {
				addTab(Tab.load(file, onSelect), false);
			} catch (Exception  e) {
				FxAlert.showErrorDialog(files, "failed to open file", e);
			}
		}
		if(tabs.size() != index)
			currentTab.set(tabs.get(index));
	}

	private void addTab(Tab tab, boolean setCurrent) {
		tabs.add(tab);
		tabsBox.getChildren().add(tab);
		tab.setOnClose(this::closeTab);
		tab.setContextMenu(closeTabsContextMenu);
		File file = openBook(tab);
		tab.setBoundBook(file);
		if(setCurrent)
			currentTab.set(tab);
	}
	public File openBook(Tab tab) {
		String s = boundBooks.getBoundBookPath(tab);
		if(s == null) return null;
		File p = new File(s);
		
		if(!p.exists())
			FxAlert.showErrorDialog(s, "Book File not found", null);
		else if(FxAlert.showConfirmDialog(s, "Open Bound Book\n"+tab.getTabTitle()+"\n"+p.getName()))
			FileOpenerNE.openFile(p);
		return p;
	}
	public void closeTab(Tab tab) {
		if(tab == null)
			return;
		
		tabclosing.forEach(c -> c.accept(tab));
		
		if(tab.isModified()) {
			ActionResult ar = tab.save(true);
			if(ar != ActionResult.NO && ar != ActionResult.SUCCESS)
				return;
		} 

		tabs.remove(tab);
		int index = tabsBox.getChildren().indexOf(tab);
		tabsBox.getChildren().remove(index);
		try {
			tab.close();
		} catch (Exception e) {
			FxAlert.showErrorDialog(null, null, e);
		}

		if(!tab.isActive())
			return;

		if(index < tabs.size())
			currentTab.set(tabs.get(index));
		else if(tabs.isEmpty())
			currentTab.set(null);
		else
			currentTab.set(tabs.get(tabs.size() - 1));
	}
	public ReadOnlyIntegerProperty tabsCountProperty() {
		return tabsCount.getReadOnlyProperty();
	}
	public boolean closeAll() {
		new ArrayList<>(tabs).forEach(this::closeTab);
		return tabs.isEmpty();
	}
	public void closeExcept(Tab tab) {
		if(tab == null)
			return;

		new ArrayList<>(tabs).stream()
		.filter(t -> t != tab)
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
	public List<Path> getJbookPaths() {
		return tabs.stream().map(Tab::getJbookPath)
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	public void forEach(Consumer<Tab> action) {
		tabs.forEach(action);
	}

	public void open(List<Path> jbookPath, Menu recentsMenu)  {
		if(jbookPath == null) {
			File file = chooseFile("select a file to open...", null, null, FileChooserType.OPEN);

			if(file == null)
				return;

			jbookPath = Collections.singletonList(file.toPath());
		}

		addTabs(jbookPath);
		List<Path> files = jbookPath;

		recentsMenu.getItems()
		.removeIf(mi -> files.contains(mi.getUserData()));
	}

	public ReadOnlyObjectProperty<Tab> currentTabProperty() {
		return currentTab.getReadOnlyProperty();
	}
	public Tab getCurrentTab() {
		return currentTab.get();
	}

	@Override
	public void changed(ObservableValue<? extends Tab> observable, Tab oldValue, Tab newTab) {
		if(oldValue != null) {
			oldValue.setActive(false);
		} if(newTab != null)
			newTab.setActive(true);
		sp.setHvalue(newTab == null ? 0 : div*(tabsBox.getChildren().indexOf(newTab) - 1));
	}
}
