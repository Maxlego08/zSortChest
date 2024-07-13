package fr.maxlego08.sort.save;

import fr.maxlego08.sort.zcore.utils.storage.Persist;
import fr.maxlego08.sort.zcore.utils.storage.Savable;

public class SortStorage implements Savable {



	/**
	 * static Singleton instance.
	 */
	private static volatile SortStorage instance;


	/**
	 * Private constructor for singleton.
	 */
	private SortStorage() {
	}

	/**
	 * Return a singleton instance of Config.
	 */
	public static SortStorage getInstance() {
		// Double lock for thread safety.
		if (instance == null) {
			synchronized (SortStorage.class) {
				if (instance == null) {
					instance = new SortStorage();
				}
			}
		}
		return instance;
	}

	public void save(Persist persist) {
		persist.save(getInstance());
	}

	public void load(Persist persist) {
		persist.loadOrSaveDefault(getInstance(), SortStorage.class);
	}

}
