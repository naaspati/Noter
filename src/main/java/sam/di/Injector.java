package sam.di;

import java.lang.annotation.Annotation;

public interface Injector {
	public <E> E instance(Class<E> type);
	public <E, A extends Annotation> E instance(Class<E> type, Class<A> qualifier);
}
