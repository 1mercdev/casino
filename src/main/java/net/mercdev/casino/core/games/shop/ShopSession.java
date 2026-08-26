package net.mercdev.casino.core.games.shop;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.games.shop.ShopGame.ShopItem;
import net.mercdev.casino.core.gui.GuiItems;
import net.mercdev.casino.core.gui.GameFx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A paginated view of ShopGame's catalog. A click on an affordable, permitted item resolves
 * the purchase immediately (deduct chips, give the item, log it) — like a Slots spin, never
 * leaving a transaction half-done, so onClose has nothing to refund.
 */
public class ShopSession extends GameSession {

    private static final int ITEMS_PER_PAGE = 45; // rows 0-4 of a 54-slot inventory
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_NEXT_PAGE = 53;

    private final ShopGame shopGame;
    private final Map<Integer, ShopItem> slotToItem = new HashMap<>();
    private int page = 0;

    protected ShopSession(CasinoPlugin plugin, Player player, ShopGame game) {
        super(plugin, player, game);
        this.shopGame = game;
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 54, "Shop");
        render();
        player.openInventory(inventory);
    }

    private void render() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.LIME_TERRACOTTA));
        }
        slotToItem.clear();

        List<ShopItem> catalog = shopGame.getCatalog();
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, catalog.size());
        for (int i = start; i < end; i++) {
            int slot = i - start;
            ShopItem item = catalog.get(i);
            inventory.setItem(slot, buildIcon(item));
            slotToItem.put(slot, item);
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV_PAGE, GuiItems.named(Material.ARROW, "§ePrevious page"));
        }
        if (end < catalog.size()) {
            inventory.setItem(SLOT_NEXT_PAGE, GuiItems.named(Material.ARROW, "§eNext page"));
        }
        renderBalance();
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    private ItemStack buildIcon(ShopItem item) {
        boolean allowed = item.permission() == null || player.hasPermission(item.permission());
        List<String> lore = new ArrayList<>(item.lore());
        if (!lore.isEmpty()) lore.add(" ");

        if (!allowed) {
            lore.add("§cYou don't have permission for this.");
            return GuiItems.named(Material.BARRIER, "§c" + item.displayName(), lore.toArray(new String[0]));
        }

        lore.add("§7Price: §f" + item.price() + " chips" + (item.amount() > 1 ? " for " + item.amount() : ""));
        lore.add("§aClick to buy");
        return GuiItems.named(item.material(), "§f" + item.displayName(), lore.toArray(new String[0]));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        List<ShopItem> catalog = shopGame.getCatalog();

        if (slot == SLOT_PREV_PAGE && page > 0) {
            page--;
            GameFx.click(player);
            render();
            return;
        }
        if (slot == SLOT_NEXT_PAGE && (page + 1) * ITEMS_PER_PAGE < catalog.size()) {
            page++;
            GameFx.click(player);
            render();
            return;
        }

        ShopItem item = slotToItem.get(slot);
        if (item != null) {
            purchase(item);
        }
    }

    private void purchase(ShopItem item) {
        if (item.permission() != null && !player.hasPermission(item.permission())) {
            player.sendMessage("§cYou don't have permission to buy that.");
            return;
        }
        if (!plugin.getEconomyManager().hasBalance(player, item.price())) {
            player.sendMessage("§cYou don't have " + item.price() + " chips.");
            return;
        }

        plugin.getEconomyManager().removeChips(player, item.price());

        ItemStack toGive = new ItemStack(item.material(), item.amount());
        player.getInventory().addItem(toGive).values()
                .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));

        plugin.getAuditLogger().logPurchase(player.getUniqueId(), item.id(), item.price(), item.amount());

        GameFx.chip(player);
        player.sendMessage("§aBought " + item.displayName() + " for " + item.price() + " chips.");
        renderBalance();
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Purchases resolve atomically on click — nothing left mid-transaction to refund.
        settled = true;
    }
}
