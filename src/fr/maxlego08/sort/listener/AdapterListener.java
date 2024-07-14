package fr.maxlego08.sort.listener;

import fr.maxlego08.sort.SortPlugin;
import fr.maxlego08.sort.zcore.utils.ZUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class AdapterListener extends ZUtils implements Listener {

    private final SortPlugin plugin;

    public AdapterListener(SortPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConnect(PlayerJoinEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onConnect(event, event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onQuit(event, event.getPlayer()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        this.plugin.getListenerAdapters()
                .forEach(adapter -> adapter.onInventoryClick(event, (Player) event.getWhoClicked()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onBlockBreak(event, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onBlockPlace(event, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onInteract(event, event.getPlayer()));
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onCraftItem(event));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onInventoryClose(event, (Player) event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onInventoryMove(event, event.getDestination(), event.getItem(), event.getSource(), event.getInitiator()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractAtEntityEvent event) {
        this.plugin.getListenerAdapters().forEach(adapter -> adapter.onInteractEntity(event, event.getPlayer(), event.getHand(), event.getRightClicked()));
    }
}
