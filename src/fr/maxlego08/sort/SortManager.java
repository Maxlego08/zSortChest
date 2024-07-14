package fr.maxlego08.sort;

import fr.maxlego08.sort.listener.ListenerAdapter;
import fr.maxlego08.sort.save.Config;
import fr.maxlego08.sort.zcore.enums.Message;
import fr.maxlego08.sort.zcore.utils.ContainerVisualize;
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

/**
 * Manages sorting chests and handling interactions with them.
 */
public class SortManager extends ListenerAdapter {

    private final ContainerVisualize containerVisualize;
    private final SortPlugin plugin;
    private final NamespacedKey itemStackKey;
    private final NamespacedKey keyBlockOwner;
    private final NamespacedKey keyChests;
    private final NamespacedKey keyChestLink;
    private final Map<Player, Block> linkChests = new HashMap<>();
    private ItemStack sortItemStack;
    private double maxDistance = 8;
    private String inventoryName = "&fChest Sorter &8(&7%amount%&8)";
    private String renameContainer = "&8Linked";
    private boolean enableRenameContainer = true;

    /**
     * Constructor for the SortManager.
     *
     * @param plugin The plugin instance.
     */
    public SortManager(SortPlugin plugin) {
        this.plugin = plugin;
        this.itemStackKey = new NamespacedKey(plugin, "sort");
        this.keyBlockOwner = new NamespacedKey(plugin, "owner");
        this.keyChests = new NamespacedKey(plugin, "chests");
        this.keyChestLink = new NamespacedKey(plugin, "linked-chest");
        this.containerVisualize = new ContainerVisualize(plugin);
    }

    /**
     * Loads the configuration settings for the SortManager.
     */
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
        this.renameContainer = configuration.getString("rename-container", "&8Linked");
        this.enableRenameContainer = configuration.getBoolean("enable-rename-container", false);
        Config.enableDebug = configuration.getBoolean("debug", false);
        Config.enableDebugTime = configuration.getBoolean("debug-time", false);
    }

    /**
     * Gives the sort item stack to a player.
     *
     * @param sender The sender giving the item.
     * @param player The player receiving the item.
     */
    public void giveItemStack(CommandSender sender, Player player) {

        player.getInventory().addItem(this.sortItemStack.clone());
        message(player, Message.COMMANDE_GIVE_RECEIVER);
        message(sender, Message.COMMANDE_GIVE_SENDER, "%player%", player.getName());
    }

    /**
     * Checks if an item stack is a sort chest item stack.
     *
     * @param itemStack The item stack to check.
     * @return True if it is a sort chest item stack, false otherwise.
     */
    private boolean isSortChestItemStack(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        return persistentDataContainer.has(this.itemStackKey);
    }

    /**
     * Handles the block place event.
     *
     * @param event  The block place event.
     * @param player The player placing the block.
     */
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

    /**
     * Handles the block break event.
     *
     * @param event  The block break event.
     * @param player The player breaking the block.
     */
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
                this.containerVisualize.clear(player);

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

    /**
     * Removes the link chests.
     *
     * @param locations The list of locations to remove links from.
     */
    private void removeLinkChests(List<Location> locations) {
        for (Location location : locations) {
            Block block = location.getBlock();
            if (block instanceof Container container) {
                PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
                if (persistentDataContainer.has(this.keyChestLink)) {
                    persistentDataContainer.remove(this.keyChestLink);
                    container.update();
                }
            }
        }
    }

    /**
     * Handles the player interact event.
     *
     * @param event  The player interact event.
     * @param player The player interacting.
     */
    @Override
    protected void onInteract(PlayerInteractEvent event, Player player) {
        Block block = event.getClickedBlock();

        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking() && block != null && player.getInventory().getItemInMainHand().getType().isAir()) {
            handleRightClick(event, player, block);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && block != null && this.linkChests.containsKey(player)) {
            handleLeftClick(event, player, block);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && this.linkChests.containsKey(player)) {
            System.out.println("Oui je veux retirer un coffre !");
        }
    }

    /**
     * Handles the right-click event.
     *
     * @param event  The player interact event.
     * @param player The player interacting.
     * @param block  The block being interacted with.
     */
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

                if (this.linkChests.containsKey(player)) {
                    this.linkChests.remove(player);
                    message(player, Message.LINK_STOP);
                    this.containerVisualize.clear(player);
                    return;
                }

                this.linkChests.put(player, block);
                message(player, Message.LINK_START);

                var locations = getLinkedChests(block);

                this.containerVisualize.clear(player);
                this.containerVisualize.spawnEntity(player, locations);
            }
        }
    }

    /**
     * Handles the left-click event.
     *
     * @param event  The player interact event.
     * @param player The player interacting.
     * @param block  The block being interacted with.
     */
    private void handleLeftClick(PlayerInteractEvent event, Player player, Block block) {
        Block sortBlock = this.linkChests.get(player);
        var state = block.getState();

        if (state instanceof Container container && sortBlock != null && !(state instanceof Hopper)) {

            event.setCancelled(true);

            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyBlockOwner)) {
                message(player, Message.LINK_ERROR_SORTER);
                return;
            }

            if (persistentDataContainer.has(this.keyChestLink)) {
                message(player, Message.LINK_ERROR_ANOTHER);
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
            updateContainerName(container);
            if (sortBlock.getState() instanceof Container sortContainer) {
                updateInventoryName(sortContainer, locations.size());
            }

            List<Location> list = new ArrayList<>();
            list.add(block.getLocation());
            this.containerVisualize.spawnEntity(player, list);

            message(player, Message.LINK_SUCCESS);
        }
    }

    /**
     * Links a chest block to the sorter.
     *
     * @param sortBlock The sorter block.
     * @param container The container to link.
     */
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

    /**
     * Final link chest block to the sorter.
     *
     * @param sortBlock The sorter block.
     * @param container The container to link.
     */
    private void finalLinkChestBlockToSorter(Block sortBlock, Container container) {
        PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
        persistentDataContainer.set(this.keyChestLink, PersistentDataType.STRING, changeLocationToString(sortBlock.getLocation()));
        container.update();
    }

    /**
     * Checks if a block is already linked.
     *
     * @param locations The list of linked locations.
     * @param block     The block to check.
     * @return True if the block is already linked, false otherwise.
     */
    private boolean isAlreadyLinked(List<Location> locations, Block block) {
        return locations.contains(block.getLocation());
    }

    /**
     * Checks if a double chest is already linked.
     *
     * @param chest     The chest to check.
     * @param locations The list of linked locations.
     * @return True if the double chest is already linked, false otherwise.
     */
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

    /**
     * Gets the linked chests for a block.
     *
     * @param block The block to get the linked chests for.
     * @return The list of linked chests.
     */
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

    /**
     * Saves the linked chests for a block.
     *
     * @param block     The block to save the linked chests for.
     * @param locations The list of linked locations.
     */
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

    /**
     * Handles the player quit event.
     *
     * @param event  The player quit event.
     * @param player The player quitting.
     */
    @Override
    protected void onQuit(PlayerQuitEvent event, Player player) {
        this.linkChests.remove(player);
        this.containerVisualize.clear(player);
    }

    /**
     * Checks if an inventory is empty.
     *
     * @param inventory The inventory to check.
     * @return True if the inventory is empty, false otherwise.
     */
    private boolean isEmpty(Inventory inventory) {
        int items = 0;
        for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                items++;
            }
        }
        return items == 0;
    }

    /**
     * Finds the amount of items in a container.
     *
     * @param container The container to check.
     * @return The amount of items in the container.
     */
    private int findAmountItem(Container container) {
        int items = 0;
        for (ItemStack itemStack : container.getInventory().getContents()) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                items++;
            }
        }
        return items;
    }

    /**
     * Handles the inventory close event.
     *
     * @param event  The inventory close event.
     * @param player The player closing the inventory.
     */
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

    /**
     * Sorts the contents of a container.
     *
     * @param container The container to sort.
     */
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

    /**
     * Sorts an item stack into possible containers.
     *
     * @param inventory          The inventory to sort.
     * @param possibleContainers The possible containers to sort into.
     * @param itemStack          The item stack to sort.
     */
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

    /**
     * Finds a container for an item stack.
     *
     * @param possibleContainer The possible containers to check.
     * @param itemStack         The item stack to find a container for.
     * @return The container for the item stack.
     */
    private Optional<Container> findContainer(List<Container> possibleContainer, ItemStack itemStack) {

        possibleContainer.removeIf(this::isInventoryFull); // On va retirer tous les inventaires pleins
        possibleContainer.sort(Comparator.comparingInt(this::findAmountItem).reversed()); // On va ensuite trier les inventaires pour prendre en premier ceux qui vont contenir des items

        // On va parcourir ensuite les inventaires pour récupérer le premier qui est compatible avec l'itemStack
        for (Container container : possibleContainer) {
            Inventory inventory = container.getInventory();

            if (isEmpty(inventory)) return Optional.of(container);

            Optional<ItemStack> optional = findFirstValidItemStack(inventory);
            if (optional.isPresent()) {
                ItemStack firstItemStack = optional.get();
                if (firstItemStack.isSimilar(itemStack)) {
                    return Optional.of(container);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Finds the first valid ItemStack in the given inventory.
     * A valid ItemStack is one that is not null and does not have an air type.
     *
     * @param inventory the inventory to search through
     * @return an Optional containing the first valid ItemStack if found, otherwise an empty Optional
     */
    private Optional<ItemStack> findFirstValidItemStack(Inventory inventory) {
        return Arrays.stream(inventory.getContents()).filter(Objects::nonNull).filter(itemStack -> !itemStack.getType().isAir()).findFirst();
    }


    /**
     * Checks if an inventory is full.
     *
     * @param container The container to check.
     * @return True if the inventory is full, false otherwise.
     */
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

    /**
     * Handles the inventory move item event.
     *
     * @param event       The inventory move item event.
     * @param destination The destination inventory.
     * @param item        The item being moved.
     * @param source      The source inventory.
     * @param initiator   The inventory initiating the move.
     */
    @Override
    protected void onInventoryMove(InventoryMoveItemEvent event, Inventory destination, ItemStack item, Inventory source, Inventory initiator) {

        if (destination.getHolder() instanceof Container container) {
            PersistentDataContainer persistentDataContainer = container.getPersistentDataContainer();
            if (persistentDataContainer.has(this.keyBlockOwner)) {
                sortContents(container);
            }
        }
    }

    /**
     * Updates the inventory name of a container.
     *
     * @param container The container to update.
     * @param amount    The amount to set in the inventory name.
     */
    private void updateInventoryName(Container container, int amount) {
        container.setCustomName(color(this.inventoryName.replace("%amount%", String.valueOf(amount))));
        container.update();
    }

    /**
     * Updates the custom name of the container if renaming is enabled.
     *
     * @param container the container to update
     */
    private void updateContainerName(Container container) {
        if (this.enableRenameContainer) {
            container.setCustomName(color(this.renameContainer));
            container.update();
        }
    }

}
