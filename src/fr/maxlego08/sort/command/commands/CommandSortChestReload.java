package fr.maxlego08.sort.command.commands;

import fr.maxlego08.sort.SortPlugin;
import fr.maxlego08.sort.command.VCommand;
import fr.maxlego08.sort.zcore.enums.Message;
import fr.maxlego08.sort.zcore.enums.Permission;
import fr.maxlego08.sort.zcore.utils.commands.CommandType;

public class CommandSortChestReload extends VCommand {

	public CommandSortChestReload(SortPlugin plugin) {
		super(plugin);
		this.setPermission(Permission.SORTCHEST_RELOAD);
		this.addSubCommand("reload", "rl");
		this.setDescription(Message.DESCRIPTION_RELOAD);
	}

	@Override
	protected CommandType perform(SortPlugin plugin) {
		
		plugin.reloadFiles();
		plugin.getSortManager().loadConfiguration();
		message(sender, Message.RELOAD);
		
		return CommandType.SUCCESS;
	}

}
