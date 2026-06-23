package nl.simplesync;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {

    private final SimpleRanksDiscordSync plugin;

    public LinkCommand(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Alleen spelers!");
            return true;
        }

        Player player = (Player) sender;

        if (plugin.isLinked(player.getUniqueId())) {
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.link-already")));
            return true;
        }

        // Start auto-link proces
        plugin.startAutoLink(player.getUniqueId());

        String uuid = player.getUniqueId().toString();
        String msg = plugin.getConfig().getString("messages.link-start", "&aStuur de bot een DM met: &e!link %uuid%")
                .replace("%uuid%", uuid);

        player.sendMessage(plugin.colorize("&8&m------------------------------"));
        player.sendMessage(plugin.colorize("&b&lDiscord Koppeling"));
        player.sendMessage(plugin.colorize(""));
        player.sendMessage(plugin.colorize("&7Stap 1: &fOpen Discord"));
        player.sendMessage(plugin.colorize("&7Stap 2: &fZoek de bot &e" + plugin.getJDA().getSelfUser().getName()));
        player.sendMessage(plugin.colorize("&7Stap 3: &fStuur DM: &e!link " + uuid));
        player.sendMessage(plugin.colorize(""));
        player.sendMessage(plugin.colorize(msg));
        player.sendMessage(plugin.colorize("&7Code verloopt over &e15 minuten"));
        player.sendMessage(plugin.colorize("&8&m------------------------------"));

        return true;
    }
}
