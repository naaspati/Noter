package sam.noter;

import sam.logging.MyLoggerFactory;

public interface InitFinalized {
	default void init(){
		MyLoggerFactory.logger(getClass()).fine("INIT");
	}
	default void finalized(){
		MyLoggerFactory.logger(getClass()).fine("FINALIZED");
	}
}
