package sam.noter.dao.zip;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.IntFunction;

import org.slf4j.Logger;

import sam.functions.IOExceptionConsumer;
import sam.myutils.Checker;
import sam.noter.Utils;

class ArrayWrap<E> {
	private static final Logger logger = Utils.logger(ArrayWrap.class);

	private int size;
	private final IntFunction<E[]> arraymaker;
	private final E[] data;
	private E[] new_data;

	public ArrayWrap(E[] data, IntFunction<E[]> arraymaker) {
		this.data = data;
		this.size = data.length;
		this.arraymaker = arraymaker;
	}

	public int oldSize() {
		return data.length;
	}
	public int size() {
		return size;
	}

	public E get(int index) {
		return index >= size ? null : data[index < data.length ? index : index - data.length];
	}
	public void set(int index, E e) {
		if(index >= size)
			throw new IndexOutOfBoundsException("id("+index+") >= size("+size+")");

		if(index < data.length)
			data[index] = e;
		else {
			int n = index - data.length;
			if(new_data == null || new_data.length < n + 1) {
				int k = Math.max(size - data.length, n);

				int size = Math.max(k + k/2, 30);
				E[] array = arraymaker.apply(size);
				if(array.length != size)
					throw new IllegalStateException();

				if(new_data != null)
					System.arraycopy(new_data, 0, array, 0, new_data.length);

				logger.debug("new_data resized: {} -> {}", new_data == null ? 0 : new_data.length, array.length);
				new_data = array;
			}
		} 
	}

	public boolean isModified() {
		return data.length != size;
	}
	public void clear() {
		if(data != null)
			Arrays.fill(data, null);
		if(new_data != null)
			Arrays.fill(new_data, null);
	}

	public int nextId() {
		return size++;
	}

	public void forEach(IOExceptionConsumer<E> consumer) throws IOException {
		int index = forEach(data, consumer, 0);
		forEach(new_data, consumer, index);
	}
	private int forEach(E[] array, IOExceptionConsumer<E> consumer, int index) throws IOException {
		if(Checker.isNotEmpty(array)) {
			for (E e : array) {
				if(e != null)
					consumer.accept(e);
				
				index++;
				if(index >= size)
					break;
			}
		}
		return index;
	}

	public boolean isEmpty() {
		return size() == 0;
	}
}
