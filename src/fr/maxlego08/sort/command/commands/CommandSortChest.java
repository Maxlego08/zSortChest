package fr.maxlego08.sort.command.commands;

import fr.maxlego08.sort.SortPlugin;
import fr.maxlego08.sort.command.VCommand;
import fr.maxlego08.sort.zcore.enums.Permission;
import fr.maxlego08.sort.zcore.utils.commands.CommandType;

public class CommandSortChest extends VCommand {

    public CommandSortChest(SortPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.SORTCHEST_USE);
        this.addSubCommand(new CommandSortChestReload(plugin));
        this.addSubCommand(new CommandSortChestGive(plugin));
    }

    @Override
    protected CommandType perform(SortPlugin plugin) {
        syntaxMessage();
        return CommandType.SUCCESS;
    }

}
