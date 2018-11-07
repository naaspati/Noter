package sam.noter.dao;

@FunctionalInterface
public interface Walker {
	public VisitResult accept(Entry e) ;
}
