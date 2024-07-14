package fr.maxlego08.sort.zcore.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContainerVisualize extends ZUtils {

    private final Plugin plugin;
    private final Team greenTeam;
    private final Map<UUID, List<Shulker>> entities = new HashMap<>();

    public ContainerVisualize(Plugin plugin) {
        this.plugin = plugin;

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        this.greenTeam = board.getTeams().stream().filter(e -> e.getName().equals("zsortgreenteam")).findFirst().orElseGet(() -> board.registerNewTeam("zsortgreenteam"));
        this.greenTeam.setColor(ChatColor.GREEN);

    }

    public void spawnEntity(Player player, List<Location> locations) {

        for (Location location : new ArrayList<>(locations)) {
            var block = location.getBlock();
            var state = block.getState();
            if (state instanceof Chest chest) {
                InventoryHolder holder = chest.getInventory().getHolder();
                if (holder instanceof DoubleChest doubleChest) {

                    if (doubleChest.getLeftSide() instanceof Chest leftChest) {
                        locations.add(leftChest.getLocation());
                    }

                    if (doubleChest.getRightSide() instanceof Chest rightChest) {
                        locations.add(rightChest.getLocation());
                    }
                }
            }
        }

        locations.forEach(location -> this.spawnEntity(player, location));
    }

    private void spawnEntity(Player player, Location location) {

        Shulker shulker = location.getWorld().spawn(location, Shulker.class);
        shulker.setInvulnerable(true);
        shulker.setAI(false);
        shulker.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, -1, 1, false, false));
        shulker.setInvisible(true);
        shulker.setCollidable(false);
        shulker.setMetadata("zsortchest", new FixedMetadataValue(this.plugin, true));

        Bukkit.getScheduler().runTaskLater(this.plugin, shulker::remove, 20 * 30); // Remove in 30 seconds

        this.addEntity(player, shulker);
    }

    private void addEntity(Player player, Shulker entity) {

        if (entity == null) {
            return;
        }

        String uuid = entity.getUniqueId().toString();
        this.greenTeam.removeEntry(uuid);
        this.greenTeam.addEntry(uuid);

        var list = this.entities.getOrDefault(player.getUniqueId(), new ArrayList<>());
        list.add(entity);
        this.entities.put(player.getUniqueId(), list);
    }

    public void clear(Player player) {
        var list = this.entities.getOrDefault(player.getUniqueId(), new ArrayList<>());
        list.forEach(Shulker::remove);
        this.entities.remove(player.getUniqueId());
    }

    public void remove(Player player, Location location) {
        var list = this.entities.getOrDefault(player.getUniqueId(), new ArrayList<>());
        list.stream().filter(shulker -> same(location, shulker.getLocation())).forEach(Shulker::remove);
        this.entities.put(player.getUniqueId(), list);
    }
}
