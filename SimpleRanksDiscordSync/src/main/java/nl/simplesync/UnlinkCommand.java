package nl.simplesync;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnlinkCommand implements CommandExecutor {

    private final SimpleRanksDiscordSync plugin;

    public UnlinkCommand(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Alleen spelers!");
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.isLinked(player.getUniqueId())) {
            player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.unlink-not-linked")));
            return true;
        }

        plugin.unlinkAccount(player.getUniqueId());
        player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.unlink-success")));
        return true;
    }
}
