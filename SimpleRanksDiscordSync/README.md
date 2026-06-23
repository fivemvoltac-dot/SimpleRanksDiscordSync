# SimpleRanksDiscordSync v2.0
## Automatische Discord ↔ Minecraft Rank Sync + Kleuren + Maandelijkse Keys

---

## ✨ Features

| Feature | Beschrijving |
|---------|-------------|
| **Auto DM Link** | Speler typt `/discordlink` → DMt bot met `!link <uuid>` → Klaar! |
| **Kleur Sync** | Discord rol kleur wordt automatisch overgenomen in-game prefix |
| **Auto Sync** | Rank wijzigingen in Discord worden direct doorgevoerd in Minecraft |
| **Maandelijkse Keys** | Per rank een unieke key, 1x per maand claimbaar |
| **Admin Link** | Admins kunnen spelers handmatig koppelen met `/admindm` |
| **Configureerbaar** | Werkt met ALLE crate plugins (AbestCrates, ExcellentCrates, etc.) |

---

## 📦 Installatie

### Vereisten
- **Spigot/Paper 1.20+**
- **SimpleRanks** (geïnstalleerd)
- **Java 17+**
- **Maven** (voor compileren)

### Stap 1: Discord Bot Aanmaken

1. Ga naar https://discord.com/developers/applications
2. Klik **"New Application"** → Noem het "Minecraft Rank Sync"
3. Ga naar **"Bot"** tab → Klik **"Add Bot"**
4. Zet ALLE drie Privileged Gateway Intents AAN:
   - ✅ PRESENCE INTENT
   - ✅ SERVER MEMBERS INTENT  
   - ✅ MESSAGE CONTENT INTENT
5. Klik **"Reset Token"** → Kopieer de token

### Stap 2: Bot Toevoegen aan Server

1. Ga naar **OAuth2** → **URL Generator**
2. Scopes: `bot`, `applications.commands`
3. Bot Permissions:
   - `Manage Roles`
   - `Read Messages/View Channels`
   - `Send Messages`
   - `Read Message History`
   - `Use Slash Commands`
4. Open de URL en autoriseer op je server

### Stap 3: Guild ID

- Rechtsklik servernaam in Discord → **"Copy Server ID"**
- (Developer Mode nodig: Instellingen → Advanced → Developer Mode)

### Stap 4: Compileer & Installeer

```bash
cd SimpleRanksDiscordSync
mvn clean package
```

Plaats `target/SimpleRanksDiscordSync-2.0.0.jar` in `plugins/`

### Stap 5: Configureer

```yaml
# plugins/SimpleRanksDiscordSync/config.yml
discord-token: "HIER_JE_BOT_TOKEN"
discord-guild-id: "HIER_JE_GUILD_ID"

# Rank mapping (Discord rol naam -> SimpleRanks rank)
rank-mapping:
  Owner: "Owner"
  Manager: "Manager"
  "Snr Admin": "SnrAdmin"
  Admin: "Admin"
  "Snr Mod": "SnrMod"
  Mod: "Mod"
  "Snr Helper": "SnrHelper"
  Helper: "Helper"
  Thunder: "Thunder"
  Diamond: "Diamond"
  Amethyst: "Amathyst"
  Member: "Member"

# Maandelijkse keys
monthly-key:
  enabled: true
  rewards:
    Owner: "legend_key"
    Manager: "legend_key"
    SnrAdmin: "epic_key"
    Admin: "epic_key"
    SnrMod: "rare_key"
    Mod: "rare_key"
    SnrHelper: "common_key"
    Helper: "common_key"
    Thunder: "thunder_key"
    Diamond: "diamond_key"
    Amathyst: "amethyst_key"
    Member: "member_key"

  # === JOUW CRATE PLUGIN COMMANDO ===
  # Vul hier het EXACTE commando in van jouw crate plugin!
  # 
  # Voorbeelden:
  # AbestCrates:     "abestcrates key give %player% %key% 1"
  # ExcellentCrates: "excellentcrates key give %player% %key% 1"
  # CrazyCrates:     "crazycrates give %player% %key% 1"
  # PhoenixCrates:   "crates giveKey %key% %player% 1"
  # EcoCrates:       "ecocrates give %player% %key% virtual 1"
  #
  commands:
    - "abestcrates key give %player% %key% 1"
    - "abestcrates givekey %player% %key% 1"
```

### Stap 6: Bot Rol Positie

In Discord Server Instellingen → Rollen:
- Sleep bot rol **BOVEN** alle ranks die gesynced moeten worden

### Stap 7: Start Server

---

## 🎮 Gebruik

### Speler koppelt zichzelf:
```
[In Minecraft]
/discordlink
→ "Stuur de bot een DM met: !link 3768ea3c-4afd-3be0-9d8b-3e16a2f8a8cc"

[In Discord DM]
!link 3768ea3c-4afd-3be0-9d8b-3e16a2f8a8cc
→ "✅ Koppeling succesvol!"
```

### Admin koppelt speler:
```
/admindm Steve @SteveDiscord
```

### Maandelijkse key claimen:
```
/claimkey
```

### Discord Slash Commands:
| Command | Beschrijving |
|---------|-------------|
| `/link <uuid>` | Koppel via slash command |
| `/unlink` | Ontkoppel |
| `/sync` | Forceer rank sync |
| `/claim` | Claim maandelijkse reward |

---

## 🎨 Kleur Sync

Wanneer een speler een Discord rol krijgt:
1. Bot leest de **hex kleur** van de Discord rol
2. Converteert naar dichtstbijzijnde Minecraft kleurcode (`&c`, `&6`, `&a`, etc.)
3. Past de `prefix` en `bracketColor` aan in SimpleRanks config
4. Reloaded SimpleRanks automatisch

**Voorbeeld:**
- Discord rol "Admin" = `#FF5555` (rood)
- In-game prefix wordt: `&cAdmin`
- Bracket color wordt: `&c`

---

## 🗝️ Maandelijkse Keys (Configureerbaar)

### Hoe het werkt:
- Reset elke **1e van de maand om 00:00**
- Speler kan maar **1x per maand** claimen
- Per rank een andere key
- **Configureer zelf** welk commando je crate plugin gebruikt

### Placeholders:
| Placeholder | Vervangen door |
|-------------|---------------|
| `%player%` | Speler naam |
| `%uuid%` | Speler UUID |
| `%key%` | Key ID uit rewards |
| `%rank%` | Rank naam |

### Voorbeeld commando's per plugin:
| Plugin | Commando |
|--------|----------|
| **AbestCrates** | `abestcrates key give %player% %key% 1` |
| **ExcellentCrates** | `excellentcrates key give %player% %key% 1` |
| **CrazyCrates** | `crazycrates give %player% %key% 1` |
| **PhoenixCrates** | `crates giveKey %key% %player% 1` |
| **EcoCrates** | `ecocrates give %player% %key% virtual 1` |
| **AdvancedCrates** | `acrate key give %player% %key% 1` |

---

## 📁 Bestanden

| Bestand | Locatie | Doel |
|---------|---------|------|
| `config.yml` | `plugins/SimpleRanksDiscordSync/` | Plugin instellingen |
| `linked-accounts.yml` | `plugins/SimpleRanksDiscordSync/` | Gekoppelde accounts |
| `monthly-claims.yml` | `plugins/SimpleRanksDiscordSync/` | Claim geschiedenis |

---

## ⚠️ Belangrijk

- Discord rol namen moeten **EXACT** matchen (hoofdlettergevoelig!)
- Bot moet `Manage Roles` permissie hebben
- Bot rol moet **boven** de te syncen rollen staan
- SimpleRanks moet geïnstalleerd zijn
- Vul het **juiste crate commando** in bij `monthly-key.commands`

---

## 🔧 Problemen Oplossen

| Probleem | Oplossing |
|----------|-----------|
| "Token niet geconfigureerd" | Vul token in config.yml |
| "Guild niet gevonden" | Controleer guild ID |
| Ranks niet gesynced | Check bot rol positie |
| Kleuren kloppen niet | Discord rol heeft geen kleur gezet |
| Key claim werkt niet | Pas `monthly-key.commands` aan in config.yml |
| "Unknown command" bij claim | Verkeerd commando ingesteld, check je crate plugin docs |

---

Gemaakt voor SimpleRanks + Discord + [jouw crate plugin] integratie
