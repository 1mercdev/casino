package net.mercdev.casino.core.game;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Per-player, per-round state for a single game. Implements InventoryHolder so the one
 * global listener ({@code CasinoGuiListener}) can identify which session owns a given
 * inventory and route events straight to it, instead of every game re-implementing that
 * lookup itself.
 * <p>
 * Lifecycle: {@link #open()} sets {@code this.inventory} and shows it to the player,
 * then zero or more {@link #onClick} calls happen, then exactly one {@link #onClose}.
 * A session is single-use — starting a new round means creating a new session via
 * {@link CasinoGame#createSession}.
 */
public abstract class GameSession implements InventoryHolder {

    protected final Player player;
    protected final CasinoGame game;
    protected Inventory inventory;

    /** True once the round has been resolved (win/loss settled) or the bet refunded. */
    protected boolean settled = false;

    protected GameSession(Player player, CasinoGame game) {
        this.player = player;
        this.game = game;
    }

    /**
     * Builds {@code this.inventory} and calls {@code player.openInventory(inventory)}.
     * Called exactly once, immediately after the session is created.
     */
    public abstract void open();

    /** Called for every click inside this session's inventory. The event is already cancelled. */
    public abstract void onClick(InventoryClickEvent event);

    /**
     * Called when the inventory closes, whether the player closed it or it was closed
     * programmatically. Implementations MUST refund the bet here if the round was not
     * already settled, so a player can never lose chips just by closing the GUI mid-round.
     */
    public abstract void onClose(InventoryCloseEvent event);

    public Player getPlayer() {
        return player;
    }

    public CasinoGame getGame() {
        return game;
    }

    public boolean isSettled() {
        return settled;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
