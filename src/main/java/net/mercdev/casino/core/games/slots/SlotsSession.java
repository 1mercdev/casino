package net.mercdev.casino.core.games.slots;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.gui.GuiItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's slot machine session. Unlike a push-your-luck game (Mines/HiLo), a spin
 * resolves completely within a single click — bet deduction, roll, and payout all happen
 * atomically inside {@link #spin()} — so a bet is never "in limbo" between clicks and
 * {@link #onClose} never needs to refund anything. The session stays open across many
 * spins, like a real machine, rather than being single-use.
 */
public class SlotsSession extends GameSession {

    private static final int SLOT_INFO = 4;
    private static final int[] REEL_SLOTS = {12, 13, 14};
    private static final int SLOT_BET_MINUS_5 = 18;
    private static final int SLOT_BET_MINUS_1 = 19;
    private static final int SLOT_BET_DISPLAY = 20;
    private static final int SLOT_BET_PLUS_1 = 21;
    private static final int SLOT_BET_PLUS_5 = 22;
    private static final int SLOT_SPIN = 24;
    private static final int SLOT_BALANCE = 26;

    private final SlotsGame slotsGame;
    private long currentBet;

    protected SlotsSession(CasinoPlugin plugin, Player player, SlotsGame game) {
        super(plugin, player, game);
        this.slotsGame = game;
        this.currentBet = plugin.getBetLimitManager().getMinBet(game.getId());
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, "Slots");
        renderAll();
        player.openInventory(inventory);
    }

    private void renderAll() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_INFO, buildInfoItem());
        for (int slot : REEL_SLOTS) {
            inventory.setItem(slot, GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§7?"));
        }
        renderBetControls();
        renderBalance();
    }

    private ItemStack buildInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Match all 3 reels for the big payout,");
        lore.add("§7any 2 matching pays a small amount.");
        lore.add(" ");
        for (SlotsGame.SlotSymbol symbol : slotsGame.getSymbols()) {
            double mult = slotsGame.scaledTripleMultiplier(symbol);
            double prob = slotsGame.probabilityOfTriple(symbol);
            lore.add(String.format("§f%s: §a%.2fx §7(1 in %.0f)", symbol.displayName(), mult, 1 / prob));
        }
        lore.add(String.format("§fAny pair: §a%.2fx", slotsGame.getScaledPairMultiplier()));
        return GuiItems.named(Material.BOOK, "§6Payout Table", lore.toArray(new String[0]));
    }

    private void renderBetControls() {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());
        inventory.setItem(SLOT_BET_MINUS_5, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-5 bet"));
        inventory.setItem(SLOT_BET_MINUS_1, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 bet"));
        inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                "§7Min " + min + " / Max " + max));
        inventory.setItem(SLOT_BET_PLUS_1, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 bet"));
        inventory.setItem(SLOT_BET_PLUS_5, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+5 bet"));
        inventory.setItem(SLOT_SPIN, GuiItems.named(Material.LEVER, "§a§lSPIN",
                "§7Click to spin for " + currentBet + " chips"));
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case SLOT_BET_MINUS_5 -> adjustBet(-5);
            case SLOT_BET_MINUS_1 -> adjustBet(-1);
            case SLOT_BET_PLUS_1 -> adjustBet(1);
            case SLOT_BET_PLUS_5 -> adjustBet(5);
            case SLOT_SPIN -> spin();
            default -> {}
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Every spin resolves fully inside a single click (see spin()) — there's never a
        // bet outstanding between clicks, so there's nothing to refund here.
        settled = true;
    }

    private void adjustBet(long delta) {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());
        currentBet = Math.max(min, Math.min(max, currentBet + delta));
        renderBetControls();
    }

    private void spin() {
        String denyReason = plugin.getBetLimitManager().checkBet(player, game.getId(), currentBet);
        if (denyReason != null) {
            player.sendMessage("§c" + denyReason);
            return;
        }
        if (!plugin.getEconomyManager().hasBalance(player, currentBet)) {
            player.sendMessage("§cYou don't have " + currentBet + " chips.");
            return;
        }
        long maxPayout = slotsGame.maxPossiblePayout(currentBet);
        if (!plugin.getHouseBankroll().canAcceptBet(currentBet, maxPayout)) {
            player.sendMessage("§cThe house can't cover a potential payout on this bet right now — try lowering it.");
            return;
        }

        plugin.getEconomyManager().removeChips(player, currentBet);
        plugin.getHouseBankroll().reserveBet(currentBet);

        SlotsGame.SlotSymbol[] result = slotsGame.rollReels();
        long payout = slotsGame.computePayout(result, currentBet);

        if (payout > 0) {
            plugin.getEconomyManager().addChips(player, payout);
        }
        plugin.getHouseBankroll().resolveBet(payout);

        plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, payout,
                payout > 0 ? "WIN" : "LOSE");
        plugin.getBetLimitManager().recordBet(player);

        boolean triple = result[0] == result[1] && result[1] == result[2];
        boolean anyPair = result[0] == result[1] || result[1] == result[2] || result[0] == result[2];
        for (int i = 0; i < REEL_SLOTS.length; i++) {
            SlotsGame.SlotSymbol symbol = result[i];
            String colour = payout > 0 ? "§a" : "§f";
            inventory.setItem(REEL_SLOTS[i], GuiItems.named(symbol.material(), colour + symbol.displayName()));
        }
        renderBetControls();
        renderBalance();

        if (triple) {
            player.sendMessage("§a§lJACKPOT! §fThree " + result[0].displayName() + "s — you won " + payout + " chips.");
        } else if (anyPair) {
            player.sendMessage("§aSmall win: §f" + payout + " chips.");
        } else {
            player.sendMessage("§7No luck this spin.");
        }
    }
}