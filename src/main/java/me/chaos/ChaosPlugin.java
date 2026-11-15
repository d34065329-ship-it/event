package me.chaos;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChaosPlugin extends JavaPlugin {

    private ChaosGame game;
    private NamespacedKey noGriefKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        noGriefKey = new NamespacedKey(this, "nogrief");
        game = new ChaosGame(this);

        getServer().getPluginManager().registerEvents(new ChaosListener(this, game), this);

        PluginCommand cmd = getCommand("chaos");
        if (cmd != null) {
            ChaosCommand handler = new ChaosCommand(this, game);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("ChaosTimer включён. Старт игры: /chaos start");
    }

    @Override
    public void onDisable() {
        if (game != null) game.stop();
    }

    public ChaosGame game() { return game; }

    public NamespacedKey noGriefKey() { return noGriefKey; }
}
