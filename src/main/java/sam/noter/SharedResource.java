package sam.noter;

public final class SharedResource {

	private static volatile SharedResource INSTANCE;

	public static SharedResource getInstance() {
		if (INSTANCE != null)
			return INSTANCE;

		synchronized (SharedResource.class) {
			if (INSTANCE != null)
				return INSTANCE;

			INSTANCE = new SharedResource();
			return INSTANCE;
		}
	}

	private SharedResource() { }
	
}
