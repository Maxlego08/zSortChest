package fr.maxlego08.sort.zcore.enums;

public enum Permission {

	SORTCHEST_USE,
	SORTCHEST_RELOAD,

    SORTCHEST_GIVE;

	private String permission;

	private Permission() {
		this.permission = this.name().toLowerCase().replace("_", ".");
	}

	public String getPermission() {
		return permission;
	}

}
