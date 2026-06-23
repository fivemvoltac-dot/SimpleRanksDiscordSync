package nl.simplesync;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SyncCommand implements CommandExecutor {

    private final SimpleRanksDiscordSync plugin;

    public SyncCommand(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!plugin.isLinked(player.getUniqueId())) {
                    player.sendMessage(plugin.colorize("&cGeen gekoppeld Discord account."));
                    return true;
                }
                plugin.syncPlayerRank(player.getUniqueId());
                player.sendMessage(plugin.colorize("&aRank gesynchroniseerd!"));
            } else {
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin-sync-start")));
                plugin.syncAllPlayers();
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.admin-sync-complete")
                        .replace("%count%", String.valueOf(plugin.getLinkedAccounts().size()))));
            }
            return true;
        }

        if (!sender.hasPermission("simpleranks.discord.admin")) {
            sender.sendMessage(plugin.colorize("&cGeen permissie."));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.colorize("&cSpeler niet gevonden!"));
            return true;
        }

        if (!plugin.isLinked(target.getUniqueId())) {
            sender.sendMessage(plugin.colorize("&cGeen gekoppeld Discord account."));
            return true;
        }

        plugin.syncPlayerRank(target.getUniqueId());
        sender.sendMessage(plugin.colorize("&aRank gesynchroniseerd voor &e" + target.getName() + "&a!"));
        return true;
    }
}
