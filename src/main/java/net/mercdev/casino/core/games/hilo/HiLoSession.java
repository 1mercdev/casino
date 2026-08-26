package net.mercdev.casino.core.games.hilo;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.gui.GuiItems;
import net.mercdev.casino.core.gui.GameFx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The first game where onClose actually has something to do: a HiLo round genuinely sits
 * "in progress" between clicks (bet placed, some multiplier already banked, next guess not
 * yet made), unlike Slots/Roulette/Coinflip where every click resolves atomically. Closing
 * mid-round auto-cashes-out at the current multiplier rather than refunding just the bet —
 * that's the fair outcome either way: if no guess has been made yet, multiplier is still
 * 1.0x, so "cash out" and "refund the bet" are the same thing; if guesses have succeeded,
 * walking away keeps what was already earned instead of forfeiting it.
 */
public class HiLoSession extends GameSession {

    private static final int SLOT_INFO = 0;
    private static final int SLOT_BET_MINUS_5 = 1;
    private static final int SLOT_BET_MINUS_1 = 2;
    private static final int SLOT_BET_DISPLAY = 3;
    private static final int SLOT_BET_PLUS_1 = 4;
    private static final int SLOT_BET_PLUS_5 = 5;
    private static final int SLOT_ACTION = 7;
    private static final int SLOT_BALANCE = 8;
    private static final int SLOT_LOWER = 11;
    private static final int SLOT_CURRENT_CARD = 13;
    private static final int SLOT_HIGHER = 15;

    private final HiLoGame hiLoGame;
    private long currentBet;
    private boolean roundActive = false;
    private int currentRank;
    private double cumulativeMultiplier = 1.0;

    protected HiLoSession(CasinoPlugin plugin, Player player, HiLoGame game) {
        super(plugin, player, game);
        this.hiLoGame = game;
        this.currentBet = plugin.getBetLimitManager().getMinBet(game.getId());
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 27, "HiLo");
        render();
        player.openInventory(inventory);
    }

    private void render() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.CYAN_TERRACOTTA));
        }
        inventory.setItem(SLOT_INFO, buildInfoItem());
        renderBalance();

        if (!roundActive) {
            long min = plugin.getBetLimitManager().getMinBet(game.getId());
            long max = plugin.getBetLimitManager().getMaxBet(game.getId());
            inventory.setItem(SLOT_BET_MINUS_5, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-5 bet"));
            inventory.setItem(SLOT_BET_MINUS_1, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 bet"));
            inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                    "§7Min " + min + " / Max " + max));
            inventory.setItem(SLOT_BET_PLUS_1, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 bet"));
            inventory.setItem(SLOT_BET_PLUS_5, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+5 bet"));
            inventory.setItem(SLOT_ACTION, GuiItems.named(Material.LEVER, "§a§lDEAL",
                    "§7Click to start a round for " + currentBet + " chips"));
            return;
        }

        long potentialPayout = Math.round(currentBet * cumulativeMultiplier);
        inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER,
                String.format("§eMultiplier: %.2fx", cumulativeMultiplier),
                "§7Bet: " + currentBet + " chips",
                "§7Potential payout: " + potentialPayout + " chips"));
        inventory.setItem(SLOT_CURRENT_CARD, GuiItems.named(HiLoGame.rankIcon(currentRank),
                "§fCurrent card: §e" + HiLoGame.rankName(currentRank)));

        if (hiLoGame.canGuess(currentRank, HiLoGame.Guess.LOWER)) {
            inventory.setItem(SLOT_LOWER, buildGuessItem(HiLoGame.Guess.LOWER));
        }
        if (hiLoGame.canGuess(currentRank, HiLoGame.Guess.HIGHER)) {
            inventory.setItem(SLOT_HIGHER, buildGuessItem(HiLoGame.Guess.HIGHER));
        }
        if (cumulativeMultiplier > 1.0) {
            inventory.setItem(SLOT_ACTION, GuiItems.glow(GuiItems.named(Material.EMERALD, "§a§lCASH OUT",
                    "§7Lock in " + potentialPayout + " chips")));
        }
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    private ItemStack buildInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Guess if the next card is higher");
        lore.add("§7or lower. Correct guesses raise your");
        lore.add("§7multiplier - cash out any time, or");
        lore.add("§7push your luck for a bigger win.");
        lore.add("§7A tie counts as a loss.");
        return GuiItems.named(Material.BOOK, "§6How to play", lore.toArray(new String[0]));
    }

    private ItemStack buildGuessItem(HiLoGame.Guess guess) {
        double probability = hiLoGame.winProbability(currentRank, guess);
        double multiplier = hiLoGame.stepMultiplier(currentRank, guess);
        String label = guess == HiLoGame.Guess.HIGHER ? "§a§lHIGHER" : "§c§lLOWER";
        Material material = guess == HiLoGame.Guess.HIGHER ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        return GuiItems.named(material, label,
                String.format("§7Chance: §f%.0f%%", probability * 100),
                String.format("§7Pays: §f%.2fx §7if correct", multiplier));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!roundActive) {
            if (slot == SLOT_BET_MINUS_5) adjustBet(-5);
            else if (slot == SLOT_BET_MINUS_1) adjustBet(-1);
            else if (slot == SLOT_BET_PLUS_1) adjustBet(1);
            else if (slot == SLOT_BET_PLUS_5) adjustBet(5);
            else if (slot == SLOT_ACTION) deal();
            return;
        }
        if (slot == SLOT_LOWER) {
            guess(HiLoGame.Guess.LOWER);
        } else if (slot == SLOT_HIGHER) {
            guess(HiLoGame.Guess.HIGHER);
        } else if (slot == SLOT_ACTION && cumulativeMultiplier > 1.0) {
            cashOut();
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (roundActive) {
            long payout = Math.round(currentBet * cumulativeMultiplier);
            plugin.getEconomyManager().addChips(player, payout);
            plugin.getHouseBankroll().resolveBet(payout);
            plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, payout, "CASHED_OUT_ON_CLOSE");
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
        // Baseline check only — a break-even cash-out (1.0x) can never exceed bankroll +
        // bet, so this rarely blocks anything. The real protection happens per-guess below,
        // since a streak's ceiling isn't known in advance.
        if (!plugin.getHouseBankroll().canAcceptBet(currentBet, currentBet)) {
            player.sendMessage("§cThe house can't cover this bet right now — try lowering it.");
            return;
        }

        plugin.getEconomyManager().removeChips(player, currentBet);
        plugin.getHouseBankroll().reserveBet(currentBet);
        plugin.getBetLimitManager().recordBet(player);

        roundActive = true;
        settled = false;
        cumulativeMultiplier = 1.0;
        currentRank = hiLoGame.drawCard();
        GameFx.lever(player);
        render();
    }

    private void guess(HiLoGame.Guess guessChoice) {
        if (!hiLoGame.canGuess(currentRank, guessChoice)) {
            return; // shouldn't be clickable when invalid, but don't trust that blindly
        }

        double nextMultiplier = cumulativeMultiplier * hiLoGame.stepMultiplier(currentRank, guessChoice);
        long nextPotentialPayout = Math.round(currentBet * nextMultiplier);

        // Checked fresh before every guess, not just once at deal(): the bet's chips were
        // already folded into the bankroll back in deal() (reserveBet), so wager=0 here —
        // this only asks whether the bankroll, as it stands right now, could still cover
        // the NEW potential payout if this next guess wins.
        if (!plugin.getHouseBankroll().canAcceptBet(0, nextPotentialPayout)) {
            player.sendMessage("§cThe house can't cover a bigger win right now — cash out to lock in your current multiplier.");
            return;
        }

        int newRank = hiLoGame.drawCard();
        boolean win = hiLoGame.isWin(currentRank, newRank, guessChoice);

        if (win) {
            cumulativeMultiplier = nextMultiplier;
            currentRank = newRank;
            GameFx.reveal(player);
            player.sendMessage("§a" + HiLoGame.rankName(newRank) + "! §7Multiplier now "
                    + String.format("%.2fx", cumulativeMultiplier));
            render();
        } else {
            plugin.getHouseBankroll().resolveBet(0);
            plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, 0, "LOSE");
            GameFx.lose(player);
            player.sendMessage("§c" + HiLoGame.rankName(newRank) + ". §7You lost this round.");
            endRound();
        }
    }

    private void cashOut() {
        long payout = Math.round(currentBet * cumulativeMultiplier);
        plugin.getEconomyManager().addChips(player, payout);
        plugin.getHouseBankroll().resolveBet(payout);
        plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, payout, "WIN");
        if (cumulativeMultiplier >= 5.0) {
            GameFx.jackpot(player);
        } else {
            GameFx.win(player);
        }
        player.sendMessage("§a§lCashed out! §f+" + payout + " chips (" + String.format("%.2fx", cumulativeMultiplier) + ")");
        endRound();
    }

    private void endRound() {
        roundActive = false;
        cumulativeMultiplier = 1.0;
        settled = true;
        render();
    }
}
