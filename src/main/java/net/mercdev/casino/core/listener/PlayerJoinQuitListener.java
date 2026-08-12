package net.mercdev.casino.core.listener;

import net.mercdev.casino.core.CasinoPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinQuitListener implements Listener {

    private final CasinoPlugin plugin;

    public PlayerJoinQuitListener(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.getEconomyManager().load(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Must run before unload(): a game's onPlayerQuit may credit a refund, and that
        // only lands correctly while this player's balance is still the cached, authoritative
        // in-memory copy. unload() flushes that cache to disk and evicts it right after.
        for (var game : plugin.getGameRegistry().all()) {
            game.onPlayerQuit(plugin, event.getPlayer());
        }
        plugin.getSessionManager().remove(event.getPlayer());
        plugin.getEconomyManager().unload(event.getPlayer().getUniqueId());
    }
}