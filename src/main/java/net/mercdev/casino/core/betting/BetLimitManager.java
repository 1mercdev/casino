package net.mercdev.casino.core.betting;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces per-game min/max bet sizes (read from config.yml's "games" section) and a
 * global cooldown between bets per player, so macro/autoclicker spam doesn't help.
 */
public class BetLimitManager {

    private final Map<String, long[]> limits = new ConcurrentHashMap<>(); // gameId -> [min, max]
    private final Map<UUID, Long> lastBetAt = new ConcurrentHashMap<>();
    private final long cooldownMs;

    public BetLimitManager(ConfigurationSection gamesSection, long cooldownMs) {
        this.cooldownMs = cooldownMs;
        if (gamesSection != null) {
            for (String gameId : gamesSection.getKeys(false)) {
                ConfigurationSection section = gamesSection.getConfigurationSection(gameId);
                if (section == null) continue;
                limits.put(gameId.toLowerCase(), new long[]{
                        section.getLong("min-bet", 1),
                        section.getLong("max-bet", 64)
                });
            }
        }
    }

    public long getMinBet(String gameId) {
        long[] range = limits.get(gameId.toLowerCase());
        return range != null ? range[0] : 1;
    }

    public long getMaxBet(String gameId) {
        long[] range = limits.get(gameId.toLowerCase());
        return range != null ? range[1] : 64;
    }

    /** Returns null if the bet is allowed, or a player-facing reason string if it isn't. */
    public String checkBet(Player player, String gameId, long amount) {
        long min = getMinBet(gameId);
        long max = getMaxBet(gameId);

        if (amount < min) return "Minimum bet for this game is " + min + " chips.";
        if (amount > max) return "Maximum bet for this game is " + max + " chips.";

        long now = System.currentTimeMillis();
        Long last = lastBetAt.get(player.getUniqueId());
        if (last != null && (now - last) < cooldownMs) {
            return "You're betting too fast, slow down a little.";
        }
        return null;
    }

    /** Call once a bet has actually been accepted, to start its cooldown. */
    public void recordBet(Player player) {
        lastBetAt.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
