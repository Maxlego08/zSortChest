package fr.maxlego08.sort;

import fr.maxlego08.sort.command.commands.CommandSortChest;
import fr.maxlego08.sort.placeholder.LocalPlaceholder;
import fr.maxlego08.sort.save.Config;
import fr.maxlego08.sort.save.MessageLoader;
import fr.maxlego08.sort.zcore.ZPlugin;

/**
 * System to create your plugins very simply Projet:
 * <a href="https://github.com/Maxlego08/TemplatePlugin">https://github.com/Maxlego08/TemplatePlugin</a>
 *
 * @author Maxlego08
 */
public class SortPlugin extends ZPlugin {

    private final SortManager sortManager = new SortManager(this);

    @Override
    public void onEnable() {

        LocalPlaceholder placeholder = LocalPlaceholder.getInstance();
        placeholder.setPrefix("zsortchest");

        this.preEnable();

        this.registerCommand("zsortchest", new CommandSortChest(this), "sortchest");
        this.addListener(this.sortManager);

        this.saveDefaultConfig();
        this.addSave(new MessageLoader(this));

        this.loadFiles();
        this.sortManager.loadConfiguration();

        this.postEnable();
    }

    @Override
    public void onDisable() {

        this.preDisable();

        this.saveFiles();

        this.postDisable();
    }

    public SortManager getSortManager() {
        return sortManager;
    }
}
