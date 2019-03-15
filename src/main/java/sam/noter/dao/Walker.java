package sam.noter.dao;

import java.util.function.Consumer;

import sam.noter.dao.api.IEntry;

@FunctionalInterface
public interface Walker<E> {
	public VisitResult accept(E e) ;

	public static Walker<IEntry> of(Consumer<IEntry> consumer) {
		return w -> {
			consumer.accept(w);
			return VisitResult.CONTINUE;
		};
	}
}
