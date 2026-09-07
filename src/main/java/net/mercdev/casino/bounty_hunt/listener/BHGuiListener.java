package net.mercdev.casino.bounty_hunt.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import net.mercdev.casino.bounty_hunt.BountyHunt;
import net.mercdev.casino.bounty_hunt.gui.BHMenuHolder;
import net.mercdev.casino.core.gui.GameFx;

public class BHGuiListener implements Listener {
    
    private final BountyHunt bountyHunt;

    public BHGuiListener(BountyHunt bHunt) {
        this.bountyHunt = bHunt;
    }

    @EventHandler 
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof BHMenuHolder menu) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();

            if (menu.isRerollSlot(event.getRawSlot())){
                if (bountyHunt.getBountyManager().reroll(player)){
                    GameFx.win(player);
                    player.sendMessage("§aSuccessfully §frerolled your bounty.");
                    event.getInventory().close();
                }
            }
        }
    }
}
