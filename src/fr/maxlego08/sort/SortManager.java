package fr.maxlego08.sort;

import fr.maxlego08.sort.listener.ListenerAdapter;
import fr.maxlego08.sort.zcore.enums.Message;
import fr.maxlego08.sort.zcore.utils.loader.ItemStackLoader;
import fr.maxlego08.sort.zcore.utils.loader.Loader;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class SortManager extends ListenerAdapter {

    private final SortPlugin plugin;
    private final NamespacedKey itemStackKey;
    private final NamespacedKey keyBlockOwner;
    private ItemStack sortItemStack;

    public SortManager(SortPlugin plugin) {
        this.plugin = plugin;
        this.itemStackKey = new NamespacedKey(plugin, "sort");
        this.keyBlockOwner = new NamespacedKey(plugin, "owner");
    }

    public void loadConfiguration() {

        YamlConfiguration configuration = (YamlConfiguration) plugin.getConfig();

        Loader<ItemStack> loader = new ItemStackLoader();
        this.sortItemStack = loader.load(configuration, "item.");
        ItemMeta itemMeta = this.sortItemStack.getItemMeta();
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        persistentDataContainer.set(this.itemStackKey, PersistentDataType.BOOLEAN, true);
        this.sortItemStack.setItemMeta(itemMeta);
    }

    public void giveItemStack(CommandSender sender, Player player) {

        player.getInventory().addItem(this.sortItemStack.clone());
        message(player, Message.COMMANDE_GIVE_RECEIVER);
        message(sender, Message.COMMANDE_GIVE_SENDER, "%player%", player.getName());
    }

    private boolean isSortChestItemStack(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        return persistentDataContainer.has(this.itemStackKey);
    }

    @Override
    protected void onBlockPlace(BlockPlaceEvent event, Player player) {
        ItemStack itemStack = event.getItemInHand();
        if (!isSortChestItemStack(itemStack)) return;

        Block block = event.getBlock();
        var state = block.getState();
        if (state instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            persistentDataContainer.set(this.itemStackKey, PersistentDataType.BOOLEAN, true);
            persistentDataContainer.set(this.keyBlockOwner, PersistentDataType.STRING, player.getUniqueId().toString());

            message(player, Message.PLACE_SORT);
        }
    }

    @Override
    protected void onBlockBreak(BlockBreakEvent event, Player player) {

        Block block = event.getBlock();
        var state = block.getState();
        if (state instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyBlockOwner)) {
                UUID ownerUUID = UUID.fromString(persistentDataContainer.get(this.keyBlockOwner, PersistentDataType.STRING));

                if (!ownerUUID.equals(player.getUniqueId())) {
                    message(player, Message.BREAK_ERROR);
                    event.setCancelled(true);
                    return;
                }

                System.out.println(block.getDrops());
            }
        }
    }
}