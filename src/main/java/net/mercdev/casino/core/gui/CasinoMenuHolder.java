package net.mercdev.casino.core.gui;

import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
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
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.BLACK_STAINED_GLASS_PANE));
        }

        int slot = FIRST_GAME_SLOT;
        for (CasinoGame game : registry.all()) {
            if (slot > LAST_GAME_SLOT) break; // hub layout only has room for a handful of icons
            ItemStack icon = game.getMenuIcon().clone();

            inventory.setItem(slot, icon);
            slotToGameId.put(slot, game.getId());
            slot++;
        }

        inventory.setItem(BALANCE_SLOT, GuiItems.named(Material.GOLD_INGOT, "§6Balance & Deposit/Withdraw",
                "§7Use /casino balance", "§7/casino deposit <amount>", "§7/casino withdraw <amount>"));
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
