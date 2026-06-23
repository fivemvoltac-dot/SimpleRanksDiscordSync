package nl.simplesync;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

public class ClaimKeyCommand implements CommandExecutor {

    private final SimpleRanksDiscordSync plugin;

    public ClaimKeyCommand(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Alleen spelers!");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (!plugin.getConfig().getBoolean("monthly-key.enabled", true)) {
            player.sendMessage(plugin.colorize("&cMaandelijkse keys zijn uitgeschakeld."));
            return true;
        }

        String currentRank = getPlayerRank(uuid);
        if (currentRank == null) {
            player.sendMessage(plugin.colorize("&cJe hebt geen rank."));
            return true;
        }

        // Alleen donator ranks krijgen maandelijkse keys
        String crateName = getCrateForRank(currentRank);
        if (crateName == null) {
            player.sendMessage(plugin.colorize("&cJe rank heeft geen maandelijkse key reward."));
            return true;
        }

        if (!plugin.canClaim(uuid)) {
            String msg = plugin.getConfig().getString("monthly-key.already-claimed", "&cAl geclaimed! Volgende: %date%")
                    .replace("%date%", plugin.getNextResetDate());
            player.sendMessage(plugin.colorize(msg));
            return true;
        }

        // AbestCrates commando: abestcrates givekey <speler> <crate> <aantal>
        String cmd = "abestcrates givekey " + player.getName() + " " + crateName + " 1";

        boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        if (!success) {
            player.performCommand("abestcrates givekey " + player.getName() + " " + crateName + " 1");
        }

        plugin.claimKey(uuid);

        String msg = plugin.getConfig().getString("monthly-key.claim-message", "&aJe hebt je maandelijkse &e%key% &ageclaimed!")
                .replace("%key%", crateName)
                .replace("%rank%", currentRank);
        player.sendMessage(plugin.colorize("&8&m------------------------------"));
        player.sendMessage(plugin.colorize("&6&lMaandelijkse Reward"));
        player.sendMessage(plugin.colorize(""));
        player.sendMessage(plugin.colorize(msg));
        player.sendMessage(plugin.colorize("&7Rank: &e" + currentRank));
        player.sendMessage(plugin.colorize("&7Crate: &e" + crateName));
        player.sendMessage(plugin.colorize("&7Volgende claim: &e" + plugin.getNextResetDate()));
        player.sendMessage(plugin.colorize("&8&m------------------------------"));

        return true;
    }

    /**
     * Alleen donator ranks krijgen keys:
     *   Thunder  → Thunder
     *   Diamond  → crate1
     *   Amathyst → crate3
     * Staff ranks krijgen GEEN keys (return null)
     */
    private String getCrateForRank(String rank) {
        switch (rank) {
            case "Thunder":
                return "Thunder";
            case "Diamond":
                return "crate1";
            case "Amathyst":
                return "crate3";
            default:
                return null; // Staff ranks en Member krijgen geen key
        }
    }

    private String getPlayerRank(UUID uuid) {
        FileConfiguration srConfig = plugin.getSimpleRanksConfig();
        if (srConfig == null) return null;
        return srConfig.getString("player-ranks." + uuid.toString(), null);
    }
}
