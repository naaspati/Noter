package sam.noter.dao.zip;

import java.util.concurrent.Callable;

import sam.myutils.ErrorRunnable;

interface Util {
	public static <E> E get(Callable<E> call, E defaultValue)  {
		try {
			return call.call();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return defaultValue;
	}
	public static boolean hide(ErrorRunnable call)  {
		try {
			call.run();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

}
