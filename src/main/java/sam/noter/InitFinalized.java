package sam.noter;

public interface InitFinalized {
	default void init(){
		Utils.logger(getClass()).debug("INIT");
	}
	default void finalized(){
		Utils.logger(getClass()).debug("FINALIZED");
	}
}
