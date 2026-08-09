package net.mercdev.casino.core.game;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each player's currently-open game session, if any. Used to stop a player from
 * having two casino GUIs conceptually "active" at once (Bukkit only lets one inventory
 * be open at a time anyway, but this also drives cleanup on quit and cooldown lookups).
 */
public class SessionManager {

    private final Map<UUID, GameSession> active = new ConcurrentHashMap<>();

    public void put(Player player, GameSession session) {
        active.put(player.getUniqueId(), session);
    }

    public GameSession get(Player player) {
        return active.get(player.getUniqueId());
    }

    public void remove(Player player) {
        active.remove(player.getUniqueId());
    }
}
