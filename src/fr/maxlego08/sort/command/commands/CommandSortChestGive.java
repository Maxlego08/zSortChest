package fr.maxlego08.sort.command.commands;

import fr.maxlego08.sort.SortPlugin;
import fr.maxlego08.sort.command.VCommand;
import fr.maxlego08.sort.zcore.enums.Message;
import fr.maxlego08.sort.zcore.enums.Permission;
import fr.maxlego08.sort.zcore.utils.commands.CommandType;
import org.bukkit.entity.Player;

public class CommandSortChestGive extends VCommand {

    public CommandSortChestGive(SortPlugin plugin) {
        super(plugin);
        this.setPermission(Permission.SORTCHEST_GIVE);
        this.addSubCommand("give", "g");
        this.setDescription(Message.DESCRIPTION_GIVE);
        this.addRequireArg("player");
    }

    @Override
    protected CommandType perform(SortPlugin plugin) {

        Player player = this.argAsPlayer(0);
        plugin.getSortManager().giveItemStack(this.sender, player);

        return CommandType.SUCCESS;
    }

}
