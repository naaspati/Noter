package sam.noter.dao;

@FunctionalInterface
public interface Walker<E> {
	public VisitResult accept(E e) ;
}
