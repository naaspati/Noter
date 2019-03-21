package sam.noter.app;

import java.nio.file.Path;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import sam.di.Injector;
import sam.nopkg.EnsureSingleton;
import sam.noter.dao.api.IRootEntry;

@Singleton
public class Actions {
	private static final EnsureSingleton singleton = new EnsureSingleton();
	{ singleton.init(); }
	
	private final Injector injector;
	
	@Inject
	public Actions(Injector injector) {
		this.injector = injector;
	}

	public void addBlankTab() {
		// TODO Auto-generated method stub
	}

	public void openChoosenFileTab() {
		// TODO Auto-generated method stub
	}
	public Object openTab(Path path) {
		// TODO Auto-generated method stub
		return null;
	}
	public Object addTabs(List<Path> path) {
		// TODO Auto-generated method stub
		return null;
	}

	public void open_containing_folder(IRootEntry currentRoot) {
		// TODO Auto-generated method stub
	}
	
	// https://github.com/naaspati/Noter/blob/master/src/main/java/sam/noter/tabs/Tab.java

	public Object reload_from_disk(IRootEntry currentRoot) {
		// TODO Auto-generated method stub
		return null;
	}

	public Object save(IRootEntry currentRoot, boolean confirmBeforeSaving) {
		// TODO Auto-generated method stub
		return null;
	}

	public void save_as(IRootEntry currentRoot, boolean confirmBeforeSaving) {
		// TODO Auto-generated method stub
		
	}

	public void saveAllTabs() {
		// TODO Auto-generated method stub
		
	}

	public void rename(IRootEntry currentRoot) {
		// TODO Auto-generated method stub
		
	}
}
