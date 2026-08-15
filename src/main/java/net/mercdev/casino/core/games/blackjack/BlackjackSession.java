package net.mercdev.casino.core.games.blackjack;

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
 * The first game where onClose deliberately does NOT try to preserve progress. HiLo and
 * Mines can fairly auto-cash-out mid-round because "your current multiplier" is a concrete,
 * already-banked value. A blackjack hand mid-decision has no such thing — the outcome is
 * genuinely unresolved (dealer hasn't played, hand value alone doesn't say who's ahead).
 * Silently auto-resolving it behind a closed GUI would be opaque in a way this framework
 * has otherwise avoided, so closing mid-hand instead voids the round and refunds the full
 * current wager (which correctly includes a double, if one was taken) — as if it never
 * happened, rather than deciding a winner nobody saw.
 */
public class BlackjackSession extends GameSession {

    private static final int SLOT_INFO = 0;
    private static final int SLOT_ACTION_1 = 1; // -5 bet (idle) / HIT (active)
    private static final int SLOT_ACTION_2 = 2; // -1 bet (idle) / STAND (active)
    private static final int SLOT_HAND_DISPLAY = 3;
    private static final int SLOT_ACTION_3 = 4; // +1 bet (idle) / DOUBLE (active, if eligible)
    private static final int SLOT_ACTION_4 = 5; // +5 bet (idle)
    private static final int SLOT_DEAL = 6;
    private static final int SLOT_BALANCE = 8;
    private static final int DEALER_ROW_START = 9;
    private static final int PLAYER_ROW_START = 18;

    private long currentBet;
    private boolean roundActive = false;
    private boolean canDouble = false;
    private boolean dealerHoleCardRevealed = false;
    private List<BlackjackGame.Rank> playerHand = new ArrayList<>();
    private List<BlackjackGame.Rank> dealerHand = new ArrayList<>();
    private String lastResultMessage;

    protected BlackjackSession(CasinoPlugin plugin, Player player, BlackjackGame game) {
        super(plugin, player, game);
        this.currentBet = plugin.getBetLimitManager().getMinBet(game.getId());
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 36, "Blackjack");
        render();
        player.openInventory(inventory);
    }

    private void render() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_INFO, buildInfoItem());
        renderBalance();
        renderCards();

        if (!roundActive) {
            long min = plugin.getBetLimitManager().getMinBet(game.getId());
            long max = plugin.getBetLimitManager().getMaxBet(game.getId());
            inventory.setItem(SLOT_ACTION_1, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-5 bet"));
            inventory.setItem(SLOT_ACTION_2, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 bet"));
            inventory.setItem(SLOT_HAND_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                    "§7Min " + min + " / Max " + max));
            inventory.setItem(SLOT_ACTION_3, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 bet"));
            inventory.setItem(SLOT_ACTION_4, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+5 bet"));
            inventory.setItem(SLOT_DEAL, GuiItems.named(Material.LEVER, "§a§lDEAL", "§7Click to deal for " + currentBet + " chips"));
        } else {
            int playerTotal = BlackjackGame.handValue(playerHand);
            inventory.setItem(SLOT_HAND_DISPLAY, GuiItems.named(Material.PAPER, "§eYour total: " + playerTotal,
                    "§7Bet: " + currentBet + " chips"));
            inventory.setItem(SLOT_ACTION_1, GuiItems.named(Material.LIME_CONCRETE, "§a§lHIT", "§7Take another card"));
            inventory.setItem(SLOT_ACTION_2, GuiItems.named(Material.RED_CONCRETE, "§c§lSTAND", "§7End your turn"));
            if (canDouble && plugin.getEconomyManager().hasBalance(player, currentBet)) {
                inventory.setItem(SLOT_ACTION_3, GuiItems.named(Material.GOLD_BLOCK, "§6§lDOUBLE",
                        "§7Double your bet, take exactly", "§7one more card, then stand"));
            }
        }
    }

    private void renderCards() {
        for (int i = 0; i < dealerHand.size() && i < 9; i++) {
            boolean hidden = (i == 1 && !dealerHoleCardRevealed);
            inventory.setItem(DEALER_ROW_START + i, hidden
                    ? GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§7?")
                    : GuiItems.named(Material.PAPER, "§f" + dealerHand.get(i).label()));
        }
        for (int i = 0; i < playerHand.size() && i < 9; i++) {
            inventory.setItem(PLAYER_ROW_START + i, GuiItems.named(Material.PAPER, "§f" + playerHand.get(i).label()));
        }
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    private ItemStack buildInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Beat the dealer without going over 21.");
        lore.add("§7Blackjack (Ace + 10) pays 3:2.");
        lore.add("§7Regular win pays 1:1. Push returns");
        lore.add("§7your bet. Dealer stands on all 17s.");
        lore.add("§7Closing mid-hand voids the round and");
        lore.add("§7refunds your bet - it's never resolved");
        lore.add("§7without you seeing the outcome.");
        if (lastResultMessage != null) {
            lore.add(" ");
            lore.add("§7Last round: " + lastResultMessage);
        }
        return GuiItems.named(Material.BOOK, "§6How to play", lore.toArray(new String[0]));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!roundActive) {
            if (slot == SLOT_ACTION_1) adjustBet(-5);
            else if (slot == SLOT_ACTION_2) adjustBet(-1);
            else if (slot == SLOT_ACTION_3) adjustBet(1);
            else if (slot == SLOT_ACTION_4) adjustBet(5);
            else if (slot == SLOT_DEAL) deal();
            return;
        }
        if (slot == SLOT_ACTION_1) hit();
        else if (slot == SLOT_ACTION_2) stand();
        else if (slot == SLOT_ACTION_3 && canDouble) doubleDown();
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (roundActive) {
            plugin.getEconomyManager().addChips(player, currentBet);
            plugin.getHouseBankroll().resolveBet(currentBet);
            plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, currentBet, "REFUND_CLOSE");
            roundActive = false;
        }
        settled = true;
    }

    private void adjustBet(long delta) {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());
        currentBet = Math.max(min, Math.min(max, currentBet + delta));
        render();
    }

    private void deal() {
        String denyReason = plugin.getBetLimitManager().checkBet(player, game.getId(), currentBet);
        if (denyReason != null) {
            player.sendMessage("§c" + denyReason);
            return;
        }
        if (!plugin.getEconomyManager().hasBalance(player, currentBet)) {
            player.sendMessage("§cYou don't have " + currentBet + " chips.");
            return;
        }
        // Covers the immediate natural-blackjack case (3:2). Double-down gets its own
        // separate check when it's actually chosen, since it can only happen after this
        // blackjack check has already passed (you can't double an already-resolved hand).
        if (!plugin.getHouseBankroll().canAcceptBet(currentBet, Math.round(currentBet * 2.5))) {
            player.sendMessage("§cThe house can't cover this bet right now — try lowering it.");
            return;
        }

        plugin.getEconomyManager().removeChips(player, currentBet);
        plugin.getHouseBankroll().reserveBet(currentBet);
        plugin.getBetLimitManager().recordBet(player);

        roundActive = true;
        settled = false;
        dealerHoleCardRevealed = false;
        canDouble = true;
        playerHand = new ArrayList<>(List.of(BlackjackGame.draw(), BlackjackGame.draw()));
        dealerHand = new ArrayList<>(List.of(BlackjackGame.draw(), BlackjackGame.draw()));

        boolean playerBJ = BlackjackGame.isBlackjack(playerHand);
        boolean dealerBJ = BlackjackGame.isBlackjack(dealerHand);
        if (playerBJ || dealerBJ) {
            dealerHoleCardRevealed = true;
            if (playerBJ && dealerBJ) {
                settleRound(currentBet, "PUSH", "§eBoth blackjack — push.");
            } else if (playerBJ) {
                settleRound(Math.round(currentBet * 2.5), "WIN", "§a§lBlackjack! §fYou win 3:2.");
            } else {
                settleRound(0, "LOSE", "§cDealer has blackjack.");
            }
            return;
        }

        render();
    }

    private void hit() {
        canDouble = false;
        playerHand.add(BlackjackGame.draw());
        if (BlackjackGame.isBust(playerHand)) {
            resolveBust();
        } else {
            render();
        }
    }

    private void stand() {
        resolveDealerAndSettle();
    }

    private void doubleDown() {
        long additionalBet = currentBet;
        if (!plugin.getEconomyManager().hasBalance(player, additionalBet)) {
            player.sendMessage("§cYou don't have enough chips to double down.");
            return;
        }
        long newTotalBet = currentBet + additionalBet;
        if (!plugin.getHouseBankroll().canAcceptBet(additionalBet, newTotalBet * 2)) {
            player.sendMessage("§cThe house can't cover a double down right now.");
            return;
        }

        plugin.getEconomyManager().removeChips(player, additionalBet);
        plugin.getHouseBankroll().reserveBet(additionalBet);
        currentBet = newTotalBet;
        canDouble = false;

        playerHand.add(BlackjackGame.draw());
        if (BlackjackGame.isBust(playerHand)) {
            resolveBust();
        } else {
            resolveDealerAndSettle();
        }
    }

    private void resolveBust() {
        dealerHoleCardRevealed = true;
        settleRound(0, "LOSE", "§cBust! You went over 21.");
    }

    private void resolveDealerAndSettle() {
        dealerHand = BlackjackGame.playDealerHand(dealerHand);
        dealerHoleCardRevealed = true;

        int playerTotal = BlackjackGame.handValue(playerHand);
        int dealerTotal = BlackjackGame.handValue(dealerHand);

        if (BlackjackGame.isBust(dealerHand) || playerTotal > dealerTotal) {
            settleRound(currentBet * 2, "WIN", "§aYou win! " + playerTotal + " vs " + dealerTotal + ".");
        } else if (playerTotal == dealerTotal) {
            settleRound(currentBet, "PUSH", "§ePush. Both " + playerTotal + ".");
        } else {
            settleRound(0, "LOSE", "§cDealer wins. " + dealerTotal + " vs " + playerTotal + ".");
        }
    }

    private void settleRound(long payout, String resultTag, String message) {
        if (payout > 0) {
            plugin.getEconomyManager().addChips(player, payout);
        }
        plugin.getHouseBankroll().resolveBet(payout);
        plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, payout, resultTag);
        player.sendMessage(message);
        lastResultMessage = message;
        roundActive = false;
        settled = true;
        render();
    }
}