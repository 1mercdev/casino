package net.mercdev.casino.core.game;

import net.mercdev.casino.core.CasinoPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

// per-player AND per-game listener. an instance for each game for each player basically
public abstract class GameSession implements InventoryHolder {

    protected final CasinoPlugin plugin;
    protected final Player player;
    protected final CasinoGame game;
    protected Inventory inventory;

    // called if the game is over (loss or win is confirmed) or refunded for whatever reason
    protected boolean settled = false;

    protected GameSession(CasinoPlugin plugin, Player player, CasinoGame game) {
        this.plugin = plugin;
        this.player = player;
        this.game = game;
    }

    //creates and opens inventory
    public abstract void open();

    // on inventory click
    public abstract void onClick(InventoryClickEvent event);

    // inventory close ***
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