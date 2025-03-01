package fr.maxlego08.sort.save;

import fr.maxlego08.sort.zcore.utils.storage.Persist;
import fr.maxlego08.sort.zcore.utils.storage.Savable;

public class Config implements Savable {

	public static boolean enableDebug = true;
	public static boolean enableDebugTime = false;
	
	/**
	 * static Singleton instance.
	 */
	private static volatile Config instance;


	/**
	 * Private constructor for singleton.
	 */
	private Config() {
	}

	/**
	 * Return a singleton instance of Config.
	 */
	public static Config getInstance() {
		// Double lock for thread safety.
		if (instance == null) {
			synchronized (Config.class) {
				if (instance == null) {
					instance = new Config();
				}
			}
		}
		return instance;
	}

	public void save(Persist persist) {

	}

	public void load(Persist persist) {

	}

}
