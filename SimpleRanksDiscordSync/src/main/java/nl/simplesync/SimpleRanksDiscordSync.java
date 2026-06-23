package nl.simplesync;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SimpleRanksDiscordSync extends JavaPlugin {

    private static SimpleRanksDiscordSync instance;
    private JDA jda;
    private Guild discordGuild;
    private FileConfiguration simpleRanksConfig;
    private File simpleRanksFile;
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToMinecraft = new ConcurrentHashMap<>();
    private final Map<UUID, DMRequest> pendingDMs = new ConcurrentHashMap<>();
    private final Map<UUID, MonthlyClaim> monthlyClaims = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private boolean enabled = false;

    // DM Request systeem
    public static class DMRequest {
        public final long expiry;
        public final String discordId; // null tot bevestigd

        public DMRequest(long expiry) {
            this.expiry = expiry;
            this.discordId = null;
        }
    }

    public static class MonthlyClaim {
        public int year;
        public int month;
        public boolean claimed;

        public MonthlyClaim(int year, int month, boolean claimed) {
            this.year = year;
            this.month = month;
            this.claimed = claimed;
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        loadLinkedAccounts();
        loadMonthlyClaims();

        String token = getConfig().getString("discord-token", "");
        if (token.equals("HIER_JE_BOT_TOKEN") || token.isEmpty()) {
            getLogger().severe("Discord token niet geconfigureerd!");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .addEventListeners(new DiscordListener(this))
                    .build();
            jda.awaitReady();

            String guildId = getConfig().getString("discord-guild-id", "");
            if (guildId.equals("HIER_JE_GUILD_ID") || guildId.isEmpty()) {
                getLogger().severe("Discord Guild ID niet geconfigureerd!");
                return;
            }

            discordGuild = jda.getGuildById(guildId);
            if (discordGuild == null) {
                getLogger().severe("Kon Discord server niet vinden!");
                return;
            }

            getLogger().info("Discord bot verbonden: " + jda.getSelfUser().getName());
            getLogger().info("Guild: " + discordGuild.getName());
            enabled = true;

            // Auto-sync scheduler
            int interval = getConfig().getInt("sync.auto-sync-interval", 5);
            if (interval > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        syncAllPlayers();
                    }
                }.runTaskTimer(this, interval * 60L * 20L, interval * 60L * 20L);
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Kon Discord bot niet starten", e);
        }

        getCommand("discordlink").setExecutor(new LinkCommand(this));
        getCommand("discordunlink").setExecutor(new UnlinkCommand(this));
        getCommand("syncranks").setExecutor(new SyncCommand(this));
        getCommand("claimkey").setExecutor(new ClaimKeyCommand(this));
        getCommand("admindm").setExecutor(new AdminDMCommand(this));
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    @Override
    public void onDisable() {
        if (jda != null) jda.shutdown();
        saveLinkedAccounts();
        saveMonthlyClaims();
    }

    public static SimpleRanksDiscordSync getInstance() { return instance; }
    public boolean isPluginEnabled() { return enabled; }
    public Guild getDiscordGuild() { return discordGuild; }
    public JDA getJDA() { return jda; }
    public Map<UUID, String> getLinkedAccounts() { return Collections.unmodifiableMap(linkedAccounts); }
    public Map<UUID, DMRequest> getPendingDMs() { return pendingDMs; }

    // ========== DM AUTO-LINK SYSTEEM ==========

    public void startAutoLink(UUID playerUuid) {
        long expiry = System.currentTimeMillis() + (getConfig().getInt("auto-link.expiry", 15) * 60 * 1000L);
        pendingDMs.put(playerUuid, new DMRequest(expiry));
    }

    public boolean completeAutoLink(UUID playerUuid, String discordId) {
        DMRequest req = pendingDMs.get(playerUuid);
        if (req == null) return false;
        if (System.currentTimeMillis() > req.expiry) {
            pendingDMs.remove(playerUuid);
            return false;
        }
        linkedAccounts.put(playerUuid, discordId);
        discordToMinecraft.put(discordId, playerUuid);
        pendingDMs.remove(playerUuid);
        saveLinkedAccounts();
        Bukkit.getScheduler().runTask(this, () -> syncPlayerRank(playerUuid));
        return true;
    }

    public void adminLink(UUID playerUuid, String discordId) {
        linkedAccounts.put(playerUuid, discordId);
        discordToMinecraft.put(discordId, playerUuid);
        saveLinkedAccounts();
        Bukkit.getScheduler().runTask(this, () -> syncPlayerRank(playerUuid));
    }

    public boolean isLinked(UUID playerUuid) { return linkedAccounts.containsKey(playerUuid); }
    public String getDiscordId(UUID playerUuid) { return linkedAccounts.get(playerUuid); }
    public UUID getMinecraftUuid(String discordId) { return discordToMinecraft.get(discordId); }

    public void unlinkAccount(UUID playerUuid) {
        String discordId = linkedAccounts.remove(playerUuid);
        if (discordId != null) discordToMinecraft.remove(discordId);
        saveLinkedAccounts();
    }

    // ========== RANK + KLEUR SYNCHRONISATIE ==========

    public void syncPlayerRank(UUID playerUuid) {
        if (!enabled) return;
        String discordId = linkedAccounts.get(playerUuid);
        if (discordId == null) return;

        Member member;
        try {
            member = discordGuild.retrieveMemberById(discordId).complete();
        } catch (Exception e) {
            getLogger().warning("Discord member niet gevonden: " + discordId);
            return;
        }
        if (member == null) return;

        String highestRank = determineHighestRank(member);
        if (highestRank == null) highestRank = getConfig().getString("sync.default-rank", "Member");

        // Kleur sync
        String colorCode = null;
        String bracketColor = null;
        if (getConfig().getBoolean("color-sync.enabled", true)) {
            Role highestRole = getHighestRole(member);
            if (highestRole != null) {
                colorCode = discordColorToMinecraft(highestRole.getColor());
                bracketColor = colorCode;
            }
        }

        updateSimpleRanks(playerUuid, highestRank, colorCode, bracketColor);

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            String msg = getConfig().getString("messages.rank-synced", "&aJe rank is gesynchroniseerd naar &e%rank%&a!")
                    .replace("%rank%", highestRank);
            player.sendMessage(colorize(msg));
        }
    }

    public String determineHighestRank(Member member) {
        Map<String, String> rankMapping = new HashMap<>();
        if (getConfig().isConfigurationSection("rank-mapping")) {
            for (String key : getConfig().getConfigurationSection("rank-mapping").getKeys(false)) {
                rankMapping.put(key, getConfig().getString("rank-mapping." + key));
            }
        }

        String highestRank = null;
        int highestPriority = Integer.MAX_VALUE;

        for (Role role : member.getRoles()) {
            String mappedRank = rankMapping.get(role.getName());
            if (mappedRank != null) {
                int priority = getRankPriority(mappedRank);
                if (priority < highestPriority) {
                    highestPriority = priority;
                    highestRank = mappedRank;
                }
            }
        }
        return highestRank;
    }

    public Role getHighestRole(Member member) {
        Map<String, String> rankMapping = new HashMap<>();
        if (getConfig().isConfigurationSection("rank-mapping")) {
            for (String key : getConfig().getConfigurationSection("rank-mapping").getKeys(false)) {
                rankMapping.put(key, getConfig().getString("rank-mapping." + key));
            }
        }

        Role highestRole = null;
        int highestPriority = Integer.MAX_VALUE;

        for (Role role : member.getRoles()) {
            String mappedRank = rankMapping.get(role.getName());
            if (mappedRank != null) {
                int priority = getRankPriority(mappedRank);
                if (priority < highestPriority) {
                    highestPriority = priority;
                    highestRole = role;
                }
            }
        }
        return highestRole;
    }

    private int getRankPriority(String rankName) {
        loadSimpleRanksConfig();
        if (simpleRanksConfig != null && simpleRanksConfig.isConfigurationSection("ranks." + rankName)) {
            return simpleRanksConfig.getInt("ranks." + rankName + ".priority", 9999);
        }
        Map<String, Integer> fallback = new HashMap<>();
        fallback.put("Owner", 0); fallback.put("Manager", 1);
        fallback.put("SnrAdmin", 2); fallback.put("Admin", 3);
        fallback.put("SnrMod", 4); fallback.put("Mod", 5);
        fallback.put("SnrHelper", 6); fallback.put("Helper", 7);
        fallback.put("Thunder", 8); fallback.put("Diamond", 9);
        fallback.put("Amathyst", 10); fallback.put("Member", 11);
        return fallback.getOrDefault(rankName, 9999);
    }

    // ========== DISCORD KLEUR -> MINECRAFT KLEUR ==========

    public String discordColorToMinecraft(Color color) {
        if (color == null) return "&f";
        int r = color.getRed(), g = color.getGreen(), b = color.getBlue();

        // Minecraft kleurcodes (approximate matching)
        Map<String, int[]> mcColors = new LinkedHashMap<>();
        mcColors.put("&0", new int[]{0, 0, 0});       // Black
        mcColors.put("&1", new int[]{0, 0, 170});      // Dark Blue
        mcColors.put("&2", new int[]{0, 170, 0});      // Dark Green
        mcColors.put("&3", new int[]{0, 170, 170});     // Dark Aqua
        mcColors.put("&4", new int[]{170, 0, 0});      // Dark Red
        mcColors.put("&5", new int[]{170, 0, 170});    // Dark Purple
        mcColors.put("&6", new int[]{255, 170, 0});    // Gold
        mcColors.put("&7", new int[]{170, 170, 170});   // Gray
        mcColors.put("&8", new int[]{85, 85, 85});      // Dark Gray
        mcColors.put("&9", new int[]{85, 85, 255});     // Blue
        mcColors.put("&a", new int[]{85, 255, 85});     // Green
        mcColors.put("&b", new int[]{85, 255, 255});    // Aqua
        mcColors.put("&c", new int[]{255, 85, 85});     // Red
        mcColors.put("&d", new int[]{255, 85, 255});     // Light Purple
        mcColors.put("&e", new int[]{255, 255, 85});     // Yellow
        mcColors.put("&f", new int[]{255, 255, 255});    // White

        String closest = "&f";
        double minDist = Double.MAX_VALUE;

        for (Map.Entry<String, int[]> entry : mcColors.entrySet()) {
            int[] mc = entry.getValue();
            double dist = Math.sqrt(Math.pow(r - mc[0], 2) + Math.pow(g - mc[1], 2) + Math.pow(b - mc[2], 2));
            if (dist < minDist) {
                minDist = dist;
                closest = entry.getKey();
            }
        }
        return closest;
    }

    // ========== SIMPLERANKS UPDATEN ==========

    public void updateSimpleRanks(UUID playerUuid, String rankName, String colorCode, String bracketColor) {
        loadSimpleRanksConfig();
        if (simpleRanksConfig == null) {
            getLogger().severe("Kon SimpleRanks config niet laden!");
            return;
        }

        // Update player rank
        simpleRanksConfig.set("player-ranks." + playerUuid.toString(), rankName);

        // Update kleur als geconfigureerd
        if (getConfig().getBoolean("color-sync.enabled", true) && colorCode != null) {
            // Update de prefix kleur van de rank zelf (alle spelers met deze rank krijgen de kleur)
            String currentPrefix = simpleRanksConfig.getString("ranks." + rankName + ".prefix", "&f" + rankName);
            // Strip oude kleurcode, voeg nieuwe toe
            String cleanPrefix = currentPrefix.replaceAll("&[0-9a-fk-or]", "");
            simpleRanksConfig.set("ranks." + rankName + ".prefix", colorCode + cleanPrefix);

            if (getConfig().getBoolean("color-sync.sync-bracket-color", true) && bracketColor != null) {
                simpleRanksConfig.set("ranks." + rankName + ".bracketColor", bracketColor);
            }
        }

        try {
            simpleRanksConfig.save(simpleRanksFile);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "simpleranks reload");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Kon SimpleRanks config niet opslaan", e);
        }
    }

    public void loadSimpleRanksConfig() {
        simpleRanksFile = new File(Bukkit.getServer().getWorldContainer().getParentFile(),
                "plugins/SimpleRanks/config.yml");
        if (!simpleRanksFile.exists()) {
            simpleRanksFile = new File("plugins/SimpleRanks/config.yml");
        }
        if (simpleRanksFile.exists()) {
            simpleRanksConfig = YamlConfiguration.loadConfiguration(simpleRanksFile);
        }
    }

    // ========== MAANDELIJKSE KEY CLAIM ==========

    public String getMonthlyReward(String rankName) {
        if (!getConfig().getBoolean("monthly-key.enabled", true)) return null;
        return getConfig().getString("monthly-key.rewards." + rankName, null);
    }

    public boolean canClaim(UUID playerUuid) {
        MonthlyClaim claim = monthlyClaims.get(playerUuid);
        LocalDateTime now = LocalDateTime.now();
        if (claim == null) return true;
        return claim.year != now.getYear() || claim.month != now.getMonthValue();
    }

    public void claimKey(UUID playerUuid) {
        LocalDateTime now = LocalDateTime.now();
        monthlyClaims.put(playerUuid, new MonthlyClaim(now.getYear(), now.getMonthValue(), true));
        saveMonthlyClaims();
    }

    public String getNextResetDate() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMonth = now.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        return nextMonth.format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
    }

    public void syncAllPlayers() {
        if (!enabled) return;
        int count = 0;
        for (Map.Entry<UUID, String> entry : linkedAccounts.entrySet()) {
            try {
                syncPlayerRank(entry.getKey());
                count++;
            } catch (Exception e) {
                getLogger().warning("Fout bij sync " + entry.getKey() + ": " + e.getMessage());
            }
        }
        getLogger().info("Auto-sync: " + count + " spelers gesynchroniseerd.");
    }

    // ========== DATA OPSLAG ==========

    private void loadLinkedAccounts() {
        File dataFile = new File(getDataFolder(), "linked-accounts.yml");
        if (!dataFile.exists()) return;
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String discordId = data.getString(key);
                if (discordId != null) {
                    linkedAccounts.put(uuid, discordId);
                    discordToMinecraft.put(discordId, uuid);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        getLogger().info(linkedAccounts.size() + " gekoppelde accounts geladen.");
    }

    private void saveLinkedAccounts() {
        File dataFile = new File(getDataFolder(), "linked-accounts.yml");
        FileConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : linkedAccounts.entrySet()) {
            data.set(entry.getKey().toString(), entry.getValue());
        }
        try { data.save(dataFile); } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Kon accounts niet opslaan", e);
        }
    }

    private void loadMonthlyClaims() {
        File dataFile = new File(getDataFolder(), "monthly-claims.yml");
        if (!dataFile.exists()) return;
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int year = data.getInt(key + ".year", 0);
                int month = data.getInt(key + ".month", 0);
                boolean claimed = data.getBoolean(key + ".claimed", false);
                monthlyClaims.put(uuid, new MonthlyClaim(year, month, claimed));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void saveMonthlyClaims() {
        File dataFile = new File(getDataFolder(), "monthly-claims.yml");
        FileConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, MonthlyClaim> entry : monthlyClaims.entrySet()) {
            data.set(entry.getKey().toString() + ".year", entry.getValue().year);
            data.set(entry.getKey().toString() + ".month", entry.getValue().month);
            data.set(entry.getKey().toString() + ".claimed", entry.getValue().claimed);
        }
        try { data.save(dataFile); } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Kon claims niet opslaan", e);
        }
    }

        public org.bukkit.configuration.file.FileConfiguration getSimpleRanksConfig() {
        loadSimpleRanksConfig();
        return simpleRanksConfig;
    }

    public String colorize(String message) {
        return message.replace("&", "\u00A7");
    }
}
