package net.mercdev.casino.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.mercdev.casino.core.CasinoPlugin;

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
        plugin.getSessionManager().remove(event.getPlayer());
        plugin.getEconomyManager().unload(event.getPlayer().getUniqueId());
    }
}
