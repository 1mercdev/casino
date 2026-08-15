package net.mercdev.casino.core.game;

import net.mercdev.casino.core.CasinoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

    /** Icon shown for this game in the casino hub GUI. */
    ItemStack getMenuIcon();

    /** Creates a fresh session for a player starting this game. Does not open the GUI. */
    GameSession createSession(CasinoPlugin plugin, Player player);

    /**
     * Called when a player disconnects, for games that keep state outside any single
     * GameSession (e.g. Coinflip's open challenges, which outlive the GUI that created
     * them). Default no-op — most games only have per-session state, already cleaned up
     * by SessionManager. Implementations here MUST NOT touch the disconnecting player's
     * economy balance except to refund/credit them (their cache entry is about to be
     * flushed and evicted right after this runs).
     */
    default void onPlayerQuit(CasinoPlugin plugin, Player player) {}
}