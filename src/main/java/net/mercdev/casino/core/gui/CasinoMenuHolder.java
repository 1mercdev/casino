package net.mercdev.casino.core.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The main casino hub: one icon per registered game, in registration order. Clicking
 * an icon asks CasinoPlugin to open that game for the clicking player; all routing is
 * handled by CasinoGuiListener, this class only builds the inventory and remembers
 * which slot maps to which game id.
 * <p>
 * With zero games registered (true for a framework-only build) this just shows the
 * balance/deposit/withdraw hint item — that's expected until the first CasinoGame is added.
 */
public class CasinoMenuHolder implements InventoryHolder {

    private static final int FIRST_GAME_SLOT = 10;
    private static final int LAST_GAME_SLOT = 16;
    private static final int BALANCE_SLOT = 22;

    private final Inventory inventory;
    private final Map<Integer, String> slotToGameId = new LinkedHashMap<>();


    public CasinoMenuHolder(GameRegistry registry) {
        this.inventory = Bukkit.createInventory(this, 27, "Casino");
        build(registry);
    }

    private void build(GameRegistry registry) {
        int slot = FIRST_GAME_SLOT;
        for (CasinoGame game : registry.all()) {
            if (slot > LAST_GAME_SLOT) break; // hub layout only has room for a handful of icons
            ItemStack icon = game.getMenuIcon().clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                icon.setItemMeta(meta);
            }
            else {
                icon = new ItemStack(Material.BARRIER);
                meta = icon.getItemMeta();
                if (meta != null){
                    meta.setDisplayName("§4Unavailable");
                    meta.setLore(List.of("This game is not available.", "Something went wrong while initializing it."));
                    icon.setItemMeta(meta);
                }
                continue;
            }
            inventory.setItem(slot, icon);
            slotToGameId.put(slot, game.getId());
            slot++;
        }

        ItemStack balanceItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = balanceItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Balance & Deposit/Withdraw");
            meta.setLore(List.of("Use /casino balance", "/casino deposit <amount>", "/casino withdraw <amount>"));
            balanceItem.setItemMeta(meta);
        }
        inventory.setItem(BALANCE_SLOT, balanceItem);
    }

    /** Which game a hub slot maps to, or null if that slot isn't a game icon. */
    public String gameIdForSlot(int slot) {
        return slotToGameId.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
