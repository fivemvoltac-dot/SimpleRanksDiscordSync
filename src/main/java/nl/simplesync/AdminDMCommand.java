package nl.simplesync;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminDMCommand implements CommandExecutor {

    private final SimpleRanksDiscordSync plugin;

    public AdminDMCommand(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("simpleranks.discord.admin")) {
            sender.sendMessage(plugin.colorize("&cGeen permissie."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.colorize("&cGebruik: /admindm <speler> <@discorduser>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(plugin.colorize("&cSpeler niet gevonden!"));
            return true;
        }

        String discordInput = args[1];
        String discordId = discordInput.replaceAll("[<@!>]", "");

        try {
            Long.parseLong(discordId);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.colorize("&cOngeldige Discord ID."));
            return true;
        }

        try {
            plugin.getJDA().retrieveUserById(discordId).complete();
        } catch (Exception e) {
            sender.sendMessage(plugin.colorize("&cDiscord gebruiker niet gevonden!"));
            return true;
        }

        plugin.adminLink(target.getUniqueId(), discordId);

        String msg = plugin.getConfig().getString("messages.admin-link-success", "&aSpeler &e%player% &ais gekoppeld aan &e%discord%")
                .replace("%player%", target.getName())
                .replace("%discord%", discordId);
        sender.sendMessage(plugin.colorize(msg));
        target.sendMessage(plugin.colorize("&aJe Discord account is handmatig gekoppeld door een admin!"));

        return true;
    }
}
