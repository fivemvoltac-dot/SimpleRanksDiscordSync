package nl.simplesync;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;

import java.util.UUID;

public class DiscordListener extends ListenerAdapter {

    private final SimpleRanksDiscordSync plugin;

    public DiscordListener(SimpleRanksDiscordSync plugin) {
        this.plugin = plugin;
        // Registreer slash commands
        plugin.getDiscordGuild().updateCommands().addCommands(
                Commands.slash("link", "Koppel je Minecraft account")
                        .addOption(OptionType.STRING, "uuid", "Je Minecraft UUID", true),
                Commands.slash("unlink", "Ontkoppel je Minecraft account"),
                Commands.slash("sync", "Forceer rank synchronisatie"),
                Commands.slash("claim", "Claim je maandelijkse reward in Minecraft")
        ).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Alleen DM's verwerken
        if (event.isFromGuild()) return;
        if (event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentRaw().trim();
        User user = event.getAuthor();

        // !link <uuid> commando in DM
        if (content.toLowerCase().startsWith("!link ")) {
            String uuidStr = content.substring(6).trim();
            handleDMLink(user, uuidStr, event);
        }
    }

    private void handleDMLink(User discordUser, String uuidStr, MessageReceivedEvent event) {
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            discordUser.openPrivateChannel().queue(channel ->
                channel.sendMessage("❌ Ongeldige UUID. Kopieer de UUID exact uit Minecraft!").queue()
            );
            return;
        }

        // Check of er een pending DM request is
        SimpleRanksDiscordSync.DMRequest req = plugin.getPendingDMs().get(playerUuid);
        if (req == null) {
            discordUser.openPrivateChannel().queue(channel ->
                channel.sendMessage("❌ Geen actief link verzoek gevonden. Typ eerst `/discordlink` in Minecraft!").queue()
            );
            return;
        }

        if (System.currentTimeMillis() > req.expiry) {
            plugin.getPendingDMs().remove(playerUuid);
            discordUser.openPrivateChannel().queue(channel ->
                channel.sendMessage("❌ Link verzoek is verlopen. Start opnieuw in Minecraft met `/discordlink`").queue()
            );
            return;
        }

        // Koppeling voltooien
        if (plugin.completeAutoLink(playerUuid, discordUser.getId())) {
            discordUser.openPrivateChannel().queue(channel ->
                channel.sendMessage("✅ **Koppeling succesvol!**\nJe Minecraft account is nu gekoppeld. Je rank wordt automatisch gesynchroniseerd.").queue()
            );

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.colorize("&a✅ Discord account gekoppeld! Je rank wordt nu gesynchroniseerd..."));
            }
        } else {
            discordUser.openPrivateChannel().queue(channel ->
                channel.sendMessage("❌ Koppeling mislukt. Probeer opnieuw.").queue()
            );
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        Member member = event.getMember();
        if (member == null) return;

        switch (command) {
            case "link":
                handleSlashLink(event, member);
                break;
            case "unlink":
                handleSlashUnlink(event, member);
                break;
            case "sync":
                handleSlashSync(event, member);
                break;
            case "claim":
                handleSlashClaim(event, member);
                break;
        }
    }

    private void handleSlashLink(SlashCommandInteractionEvent event, Member member) {
        String uuidStr = event.getOption("uuid").getAsString();
        UUID playerUuid;
        try {
            playerUuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            event.reply("❌ Ongeldige UUID!").setEphemeral(true).queue();
            return;
        }

        SimpleRanksDiscordSync.DMRequest req = plugin.getPendingDMs().get(playerUuid);
        if (req == null) {
            event.reply("❌ Geen actief link verzoek. Typ `/discordlink` in Minecraft eerst.").setEphemeral(true).queue();
            return;
        }

        if (plugin.completeAutoLink(playerUuid, member.getId())) {
            event.reply("✅ Koppeling succesvol!").setEphemeral(true).queue();
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.colorize("&a✅ Discord gekoppeld! Rank sync gestart..."));
            }
        } else {
            event.reply("❌ Koppeling mislukt.").setEphemeral(true).queue();
        }
    }

    private void handleSlashUnlink(SlashCommandInteractionEvent event, Member member) {
        UUID uuid = plugin.getMinecraftUuid(member.getId());
        if (uuid != null) {
            plugin.unlinkAccount(uuid);
            event.reply("✅ Ontkoppeld.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Geen gekoppeld account.").setEphemeral(true).queue();
        }
    }

    private void handleSlashSync(SlashCommandInteractionEvent event, Member member) {
        UUID uuid = plugin.getMinecraftUuid(member.getId());
        if (uuid != null) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.syncPlayerRank(uuid));
            event.reply("🔄 Rank sync gestart!").setEphemeral(true).queue();
        } else {
            event.reply("❌ Geen gekoppeld account. Gebruik eerst `/link` of DM `!link <uuid>`.").setEphemeral(true).queue();
        }
    }

    private void handleSlashClaim(SlashCommandInteractionEvent event, Member member) {
        UUID uuid = plugin.getMinecraftUuid(member.getId());
        if (uuid == null) {
            event.reply("❌ Geen gekoppeld Minecraft account.").setEphemeral(true).queue();
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            event.reply("❌ Je moet online zijn in Minecraft om te claimen.").setEphemeral(true).queue();
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            player.performCommand("claimkey");
        });
        event.reply("🎁 Claim commando verstuurd naar Minecraft!").setEphemeral(true).queue();
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        if (!plugin.isPluginEnabled()) return;
        UUID uuid = plugin.getMinecraftUuid(event.getUser().getId());
        if (uuid == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.syncPlayerRank(uuid), 20L);
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (!plugin.isPluginEnabled()) return;
        if (!plugin.getConfig().getBoolean("sync.remove-rank-on-role-remove", true)) return;
        UUID uuid = plugin.getMinecraftUuid(event.getUser().getId());
        if (uuid == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.syncPlayerRank(uuid), 20L);
    }
}
