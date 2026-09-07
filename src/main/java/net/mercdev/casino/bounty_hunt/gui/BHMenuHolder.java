package net.mercdev.casino.bounty_hunt.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.bukkit.inventory.Inventory;

import net.mercdev.casino.bounty_hunt.BountyHunt;
import net.mercdev.casino.bounty_hunt.types.Bounty;
import net.mercdev.casino.core.gui.GuiItems;

public class BHMenuHolder implements InventoryHolder{
    
    private static final int VICTIM_SLOT = 11;
    private static final int STATUS_SLOT = 12;
    private static final int INFO_SLOT = 14;
    private static final int BOUNTY_TIMER_SLOT = 16;
    private static final int REROLL_SLOT = 17;
    private static final int REROLL_TIMER_SLOT = 18;

    private BountyHunt bountyHunt;
    private Inventory inventory;

    public BHMenuHolder(BountyHunt bHunt) {
        this.inventory = Bukkit.createInventory(this, 27, "Bounty Hunt");
        this.bountyHunt = bHunt;
        build(bountyHunt);
    }

    private void build(BountyHunt bHunt) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        inventory.setItem(INFO_SLOT, GuiItems.glow(GuiItems.named(Material.MAP, "§bBounty Hunt", "§7Kill your objective for rewards.")));

        Player player = (Player) inventory.getHolder();
        Bounty bounty = bHunt.getBountyManager().getBounty(player.getUniqueId());
        OfflinePlayer victim = Bukkit.getOfflinePlayer(bounty.getVictimUuid());

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(victim);
                meta.setDisplayName("§4Target: §f" + victim.getName());
                head.setItemMeta(meta);
            }
        inventory.setItem(VICTIM_SLOT, head);


        Material statusMaterial;
        String statusName;
        switch (bounty.getBountyStatus()) {
            case CLAIMED:
                statusMaterial = Material.YELLOW_TERRACOTTA;
                statusName = "§eClaimed";
            case EXPIRED:
                statusMaterial = Material.RED_TERRACOTTA;
                statusName = "§cExpired";
            case ACTIVE:
                statusMaterial = Material.LIME_TERRACOTTA;
                statusName = "§aActive";
            case REROLLED:
                statusMaterial = Material.LIGHT_BLUE_TERRACOTTA;
                statusName = "§1Rerolled";
            default:
                statusMaterial = Material.BARRIER;
                statusName = "Unknown";
        }
        inventory.setItem(STATUS_SLOT, GuiItems.named(statusMaterial, statusName, ""));

        String bountyTimerString;
        long expiryMs = bounty.getExpiryTimeMillis();
        long msLeft = expiryMs - System.currentTimeMillis();
        if (msLeft < 0) {
            bountyTimerString = "§cBounty expired!";
        }
        else {
            int hours = (int) (msLeft / (1000L * 60L * 60L));
            int mins = (int) (msLeft / (1000L * 60L) % 60);
            bountyTimerString = "§f" + hours + "h " + mins + "m left until the bounty expires!";
        }

        inventory.setItem(BOUNTY_TIMER_SLOT, GuiItems.named(Material.COMPASS, "Time left until bounty expires:", bountyTimerString));

        String rerollTimerString;
        long lastRerolled = bHunt.getDatabase().getLastRerollTime(player.getUniqueId());
        long elapsed = System.currentTimeMillis() - lastRerolled;
        long rerollCooldown = bHunt.getCasinoPlugin().getConfig().getConfigurationSection("bounty-hunt").getInt("reroll-cooldown-minutes") * 60L * 1000L;
        if (elapsed < rerollCooldown){
            int mins = (int) ((rerollCooldown - elapsed) / (1000L * 60L));
            int seconds = (int) ((rerollCooldown - elapsed) / 1000L) % 60;
            rerollTimerString = "§f" + mins + "m " + seconds + "s left until next reroll";
        }
        else 
            rerollTimerString = "§dReroll available!";
        inventory.setItem(REROLL_TIMER_SLOT, GuiItems.named(Material.CLOCK, "Time left until next reroll:", rerollTimerString));
    }

    public boolean isRerollSlot(int slot){
        return slot == REROLL_SLOT;
    }

    @Override 
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
