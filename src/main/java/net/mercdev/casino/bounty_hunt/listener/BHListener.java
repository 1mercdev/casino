package net.mercdev.casino.bounty_hunt.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import net.mercdev.casino.bounty_hunt.BountyHunt;

public class BHListener implements Listener{
    
    private final BountyHunt bHunt;

    public BHListener(BountyHunt bHunt) {
        this.bHunt = bHunt;
    }

    @EventHandler 
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            bHunt.getBountyManager().tryBountyClaim(killer, victim);
        }
    }

    @EventHandler 
    public void onJoin(PlayerJoinEvent event) {
        bHunt.getBountyManager().loadPlayer(event.getPlayer());
    }

    @EventHandler 
    public void onQuit(PlayerQuitEvent event) {
        bHunt.getBountyManager().unloadPlayer(event.getPlayer());
    }
}
