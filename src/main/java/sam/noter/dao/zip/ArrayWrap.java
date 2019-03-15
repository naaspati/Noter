package sam.noter.dao.zip;

import java.util.Arrays;
import java.util.function.IntFunction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class ArrayWrap<E> {
	private static final Logger logger = LogManager.getLogger(ArrayWrap.class);

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
}
