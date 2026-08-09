package net.mercdev.casino.core.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.gui.CasinoMenuHolder;

/**
 * Single global listener that routes every casino-related inventory event to the right
 * place, so individual games never need to register their own click/close listeners.
 * All clicks in a casino inventory are cancelled by default — these are menus, not
 * survival storage.
 */
public class CasinoGuiListener implements Listener {

    private final CasinoPlugin plugin;

    public CasinoGuiListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof GameSession session) {
            event.setCancelled(true);
            session.onClick(event);
            return;
        }

        if (holder instanceof CasinoMenuHolder hub) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            String gameId = hub.gameIdForSlot(event.getRawSlot());
            if (gameId == null) return;

            plugin.getGameRegistry().get(gameId).ifPresent(game -> plugin.openGame(player, game));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GameSession session) {
            session.onClose(event);
            if (event.getPlayer() instanceof Player player) {
                plugin.getSessionManager().remove(player);
            }
        }
    }
}
