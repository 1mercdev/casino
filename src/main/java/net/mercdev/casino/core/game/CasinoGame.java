package net.mercdev.casino.core.game;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.mercdev.casino.core.CasinoPlugin;

/**
 * Represents one type of casino game (e.g. Slots, Roulette, Coinflip).
 * <p>
 * A CasinoGame instance is a singleton controller shared by every player. It never holds
 * per-round state itself — that lives in a {@link GameSession} created fresh for each
 * player each time they start a round, via {@link #createSession}.
 */
public interface CasinoGame {

    /** Stable lowercase identifier, e.g. "slots". Used in config.yml, commands, and audit logs. */
    String getId();

    /** Player-facing name, e.g. "Slots". */
    String getDisplayName();

    /** Icon shown for this game in the casino hub GUI. */
    ItemStack getMenuIcon();

    /** Creates a fresh session for a player starting this game. Does not open the GUI. */
    GameSession createSession(CasinoPlugin plugin, Player player);
}
