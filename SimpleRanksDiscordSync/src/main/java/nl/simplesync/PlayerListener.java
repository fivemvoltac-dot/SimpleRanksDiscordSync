package nl.simplesync;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    private final SimpleRanksDiscordSync plugin;

    public PlayerListener(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.isPluginEnabled()) return;
        if (!plugin.getConfig().getBoolean("sync.sync-on-join", true)) return;
        if (!plugin.isLinked(event.getPlayer().getUniqueId())) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.syncPlayerRank(event.getPlayer().getUniqueId());
        }, 60L);
    }
}
