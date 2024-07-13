package fr.maxlego08.sort;

import fr.maxlego08.sort.listener.ListenerAdapter;
import fr.maxlego08.sort.save.Config;
import fr.maxlego08.sort.zcore.enums.Message;
import fr.maxlego08.sort.zcore.utils.loader.ItemStackLoader;
import fr.maxlego08.sort.zcore.utils.loader.Loader;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class SortManager extends ListenerAdapter {

    private final SortPlugin plugin;
    private final NamespacedKey itemStackKey;
    private final NamespacedKey keyBlockOwner;
    private final NamespacedKey keyChests;
    private final NamespacedKey keyChestLink;
    private final Map<Player, Block> linkChests = new HashMap<>();
    private ItemStack sortItemStack;
    private double maxDistance = 8;
    private String inventoryName = "&fChest Sorter &8(&7%amount%&8)";

    public SortManager(SortPlugin plugin) {
        this.plugin = plugin;
        this.itemStackKey = new NamespacedKey(plugin, "sort");
        this.keyBlockOwner = new NamespacedKey(plugin, "owner");
        this.keyChests = new NamespacedKey(plugin, "chests");
        this.keyChestLink = new NamespacedKey(plugin, "linked-chest");
    }

    public void loadConfiguration() {

        YamlConfiguration configuration = (YamlConfiguration) plugin.getConfig();

        Loader<ItemStack> loader = new ItemStackLoader();
        this.sortItemStack = loader.load(configuration, "item.");
        ItemMeta itemMeta = this.sortItemStack.getItemMeta();
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        persistentDataContainer.set(this.itemStackKey, PersistentDataType.BOOLEAN, true);
        this.sortItemStack.setItemMeta(itemMeta);

        this.maxDistance = configuration.getDouble("max-distance", 8.0);
        this.inventoryName = configuration.getString("inventory-name", "&fChest Sorter &8(&7%amount%&8)");
        Config.enableDebug = configuration.getBoolean("debug", false);
        Config.enableDebugTime = configuration.getBoolean("debug-time", false);
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
            persistentDataContainer.set(this.keyChests, PersistentDataType.STRING, "");
            container.update();

            updateInventoryName(container, 0);

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
                    message(player, Message.BREAK_ERROR_OWNER);
                    event.setCancelled(true);
                    return;
                }

                Inventory inventory = container.getInventory();
                if (!isEmpty(inventory)) {
                    message(player, Message.BREAK_ERROR_EMPTY);
                    event.setCancelled(true);
                    return;
                }

                var locations = getLinkedChests(block);
                removeLinkChests(locations);

                block.getWorld().dropItemNaturally(block.getLocation(), this.sortItemStack.clone());
                event.setDropItems(false);

            } else if (persistentDataContainer.has(this.keyChestLink)) {

                Location location = changeStringLocationToLocation(persistentDataContainer.get(this.keyChestLink, PersistentDataType.STRING));
                Block sortBlock = location.getBlock();

                List<Location> locations = getLinkedChests(sortBlock);
                locations.removeIf(currentLocation -> same(currentLocation, block.getLocation()));

                saveLinkedChests(sortBlock, locations);
                if (sortBlock.getState() instanceof Container sortContainer) {
                    updateInventoryName(sortContainer, locations.size());
                }

                message(player, Message.UNLINK_SUCCESS);
            }
        }
    }

    private void removeLinkChests(List<Location> locations) {
        for (Location location : locations) {
            Block block = location.getBlock();
            if (block instanceof Container container) {
                PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
                if (persistentDataContainer.has(this.keyChestLink)) {
                    persistentDataContainer.remove(this.keyChests);
                    container.update();
                }
            }
        }
    }

    @Override
    protected void onInteract(PlayerInteractEvent event, Player player) {
        Block block = event.getClickedBlock();

        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() && block != null && player.getInventory().getItemInMainHand().getType().isAir()) {
            handleRightClick(event, player, block);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && block != null && this.linkChests.containsKey(player)) {
            handleLeftClick(event, player, block);
        }
    }

    private void handleRightClick(PlayerInteractEvent event, Player player, Block block) {
        var state = block.getState();
        if (state instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyBlockOwner)) {
                UUID ownerUUID = UUID.fromString(persistentDataContainer.get(this.keyBlockOwner, PersistentDataType.STRING));

                if (!ownerUUID.equals(player.getUniqueId())) {
                    message(player, Message.LINK_ERROR_OWNER);
                    event.setCancelled(true);
                    return;
                }

                event.setCancelled(true);
                this.linkChests.put(player, block);
                message(player, Message.LINK_START);
            }
        }
    }

    private void handleLeftClick(PlayerInteractEvent event, Player player, Block block) {
        Block sortBlock = this.linkChests.get(player);
        var state = block.getState();

        if (state instanceof Container container && sortBlock != null && !(state instanceof Hopper)) {

            event.setCancelled(true);
            this.linkChests.remove(player);

            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyBlockOwner)) {
                message(player, Message.LINK_ERROR_SORTER);
                return;
            }

            List<Location> locations = getLinkedChests(sortBlock);
            if (isAlreadyLinked(locations, block)) {
                message(player, Message.LINK_ERROR_ALREADY);
                return;
            }

            if (container instanceof Chest chest && isDoubleChestLinked(chest, locations)) {
                message(player, Message.LINK_ERROR_ALREADY);
                return;
            }

            if (block.getLocation().distance(sortBlock.getLocation()) > this.maxDistance) {
                message(player, Message.LINK_ERROR_DISTANCE);
                return;
            }

            locations.add(block.getLocation());

            saveLinkedChests(sortBlock, locations);
            linkChestBlockToSorter(sortBlock, container);
            if (sortBlock.getState() instanceof Container sortContainer) {
                updateInventoryName(sortContainer, locations.size());
            }

            message(player, Message.LINK_SUCCESS);
        }
    }

    private void linkChestBlockToSorter(Block sortBlock, Container container) {
        if (container instanceof Chest chest && chest.getInventory() instanceof DoubleChestInventory doubleChestInventory) {
            DoubleChest doubleChest = doubleChestInventory.getHolder();

            if (doubleChest.getLeftSide() instanceof Chest leftSideChest) {
                finalLinkChestBlockToSorter(sortBlock, leftSideChest);
            }

            if (doubleChest.getRightSide() instanceof Chest rightSideChest) {
                finalLinkChestBlockToSorter(sortBlock, rightSideChest);
            }
        } else {

            finalLinkChestBlockToSorter(sortBlock, container);
        }
    }

    private void finalLinkChestBlockToSorter(Block sortBlock, Container container) {
        PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
        persistentDataContainer.set(this.keyChestLink, PersistentDataType.STRING, changeLocationToString(sortBlock.getLocation()));
        container.update();
    }

    private boolean isAlreadyLinked(List<Location> locations, Block block) {
        return locations.contains(block.getLocation());
    }

    private boolean isDoubleChestLinked(Chest chest, List<Location> locations) {
        if (chest.getInventory() instanceof DoubleChestInventory doubleChestInventory) {
            DoubleChest doubleChest = doubleChestInventory.getHolder();

            if (doubleChest.getLeftSide() instanceof Chest leftSideChest) {
                return locations.contains(leftSideChest.getBlock().getLocation());
            }

            if (doubleChest.getRightSide() instanceof Chest rightSideChest) {
                return locations.contains(rightSideChest.getBlock().getLocation());
            }
        }
        return false;
    }


    private List<Location> getLinkedChests(Block block) {
        var state = block.getState();
        if (state instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyChests)) {

                String value = persistentDataContainer.get(this.keyChests, PersistentDataType.STRING);
                if (value == null || value.isEmpty()) return new ArrayList<>();

                String[] values = value.split("\\|");
                return Arrays.stream(values).map(this::changeStringLocationToLocation).filter(Objects::nonNull).collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }

    private void saveLinkedChests(Block block, List<Location> locations) {
        var state = block.getState();
        if (state instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyChests)) {

                String value = locations.stream().map(this::changeLocationToString).collect(Collectors.joining("|"));

                persistentDataContainer.set(this.keyChests, PersistentDataType.STRING, value);
                container.update();
            }
        }
    }

    @Override
    protected void onQuit(PlayerQuitEvent event, Player player) {
        this.linkChests.remove(player);
    }

    private boolean isEmpty(Inventory inventory) {
        int items = 0;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                items++;
            }
        }
        return items == 0;
    }

    private int findAmountItem(Container container) {
        int items = 0;
        for (ItemStack itemStack : container.getInventory().getContents()) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                items++;
            }
        }
        return items;
    }

    @Override
    protected void onInventoryClose(InventoryCloseEvent event, Player player) {

        var inventory = event.getInventory();
        if (inventory.getHolder() instanceof Container container) {

            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyChests)) {
                sortContents(container);
            }
        }
    }

    private void sortContents(Container container) {

        Inventory inventory = container.getInventory();

        if (isEmpty(inventory)) return;

        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && !itemStack.getType().isAir()) {

                List<Location> locations = this.getLinkedChests(container.getBlock());
                if (locations.isEmpty()) return;

                List<Container> possibleContainers = locations.stream().filter(Objects::nonNull) // We’ll make sure no rentals are zero
                        .map(Location::getBlock)// We turn the location into a block
                        .filter(block -> block.getState() instanceof Container) // Check that the block is a Container
                        .map(block -> (Container) block.getState())// We turn the block into a Container
                        .toList();

                sortItemStack(inventory, possibleContainers, itemStack);
            }
        }
    }

    private void sortItemStack(Inventory inventory, List<Container> possibleContainers, ItemStack itemStack) {

        Optional<Container> optional = findContainer(new ArrayList<>(possibleContainers), itemStack);
        if (optional.isPresent()) {

            Container container = optional.get();
            Inventory containerInventory = container.getInventory();

            Map<Integer, ItemStack> results = containerInventory.addItem(itemStack);

            if (results.isEmpty()) {
                inventory.remove(itemStack);
            }
        }
    }

    private Optional<Container> findContainer(List<Container> possibleContainer, ItemStack itemStack) {

        possibleContainer.removeIf(this::isInventoryFull); // On va retirer tous les inventaires pleins
        possibleContainer.sort(Comparator.comparingInt(this::findAmountItem).reversed()); // On va ensuite trier les inventaires pour prendre en premier ceux qui vont contenir des items

        // On va parcourir ensuite les inventaires pour récupérer le premier qui est compatible avec l'itemStack
        for (Container container : possibleContainer) {
            Inventory inventory = container.getInventory();

            if (isEmpty(inventory)) return Optional.of(container);

            ItemStack firstItemStack = inventory.getContents()[0];
            if (firstItemStack != null && firstItemStack.isSimilar(itemStack)) {
                return Optional.of(container);
            }
        }

        return Optional.empty();
    }

    public boolean isInventoryFull(Container container) {
        for (ItemStack item : container.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) {
                return false;
            }
            if (item.getAmount() < item.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onInventoryMove(InventoryMoveItemEvent event, Inventory destination, ItemStack item, Inventory source, Inventory initiator) {

        if (destination.getHolder() instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyBlockOwner)) {
                sortContents(container);
            }
        }
    }

    private void updateInventoryName(Container container, int amount) {
        container.setCustomName(color(this.inventoryName.replace("%amount%", String.valueOf(amount))));
        container.update();
    }
}