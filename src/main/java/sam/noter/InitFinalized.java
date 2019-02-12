package sam.noter;

import org.apache.logging.log4j.LogManager;

public interface InitFinalized {
	default void init(){
		LogManager.getLogger(getClass()).debug("INIT");
	}
	default void finalized(){
		LogManager.getLogger(getClass()).debug("FINALIZED");
	}
}
