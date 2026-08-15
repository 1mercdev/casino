package net.mercdev.casino.core.games.mines;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The 5x5 grid sits centered in a 54-slot inventory (columns 2-6, rows 1-5); the side
 * columns hold bet/mine-count controls (row 0) and live status (margins). Clicking any
 * tile while idle both starts a new round AND reveals that tile as the first pick — the
 * same "click commits" pattern Roulette uses, no separate DEAL button needed since the
 * first click naturally doubles as the first move (unlike HiLo, where the first card
 * reveal has no guess attached to it yet).
 * <p>
 * After a round ends (bust or cash-out), every mine position is revealed — not just the
 * one that was hit — so the board's legitimacy is visible rather than asserted.
 */
public class MinesSession extends GameSession {

    private static final int SLOT_INFO = 0;
    private static final int SLOT_BET_MINUS_5 = 1;
    private static final int SLOT_BET_MINUS_1 = 2;
    private static final int SLOT_BET_DISPLAY = 3;
    private static final int SLOT_BET_PLUS_1 = 4;
    private static final int SLOT_BET_PLUS_5 = 5;
    private static final int SLOT_MINES_MINUS = 6;
    private static final int SLOT_MINES_DISPLAY = 7;
    private static final int SLOT_MINES_PLUS = 8;
    private static final int SLOT_SAFE_COUNT = 10;
    private static final int SLOT_BALANCE = 17;
    private static final int SLOT_POTENTIAL_PAYOUT = 26;
    private static final int SLOT_MULTIPLIER = 35;
    private static final int SLOT_CASH_OUT = 53;
    private static final int[] MARGIN_FILLER_SLOTS = {9, 18, 27, 36, 45, 19, 28, 37, 46, 16, 25, 34, 43, 52, 44};

    private static final Map<Integer, Integer> SLOT_TO_TILE = buildSlotMap();

    private static int gridSlot(int index) {
        int gridRow = index / 5;
        int gridCol = index % 5;
        return 11 + 9 * gridRow + gridCol;
    }

    private static Map<Integer, Integer> buildSlotMap() {
        Map<Integer, Integer> map = new HashMap<>();
        for (int index = 0; index < MinesGame.GRID_SIZE; index++) {
            map.put(gridSlot(index), index);
        }
        return map;
    }

    private final MinesGame minesGame;
    private long currentBet;
    private int mineCount = MinesGame.DEFAULT_MINES;
    private boolean roundActive = false;
    private Set<Integer> minePositions = Set.of();
    private Set<Integer> revealedSafe = new HashSet<>();
    private Set<Integer> revealedMinesAfterRound = Set.of();
    private Integer lastExplodedIndex;
    private double cumulativeMultiplier = 1.0;

    protected MinesSession(CasinoPlugin plugin, Player player, MinesGame game) {
        super(plugin, player, game);
        this.minesGame = game;
        this.currentBet = plugin.getBetLimitManager().getMinBet(game.getId());
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 54, "Mines");
        render();
        player.openInventory(inventory);
    }

    private void render() {
        renderControlsRow();
        renderGrid();
        renderStatus();
    }

    private void renderControlsRow() {
        inventory.setItem(SLOT_INFO, buildInfoItem());
        if (!roundActive) {
            long min = plugin.getBetLimitManager().getMinBet(game.getId());
            long max = plugin.getBetLimitManager().getMaxBet(game.getId());
            inventory.setItem(SLOT_BET_MINUS_5, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-5 bet"));
            inventory.setItem(SLOT_BET_MINUS_1, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 bet"));
            inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                    "§7Min " + min + " / Max " + max));
            inventory.setItem(SLOT_BET_PLUS_1, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 bet"));
            inventory.setItem(SLOT_BET_PLUS_5, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+5 bet"));
            inventory.setItem(SLOT_MINES_MINUS, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 mine"));
            inventory.setItem(SLOT_MINES_DISPLAY, GuiItems.named(Material.TNT, "§eMines: " + mineCount,
                    "§7" + (MinesGame.GRID_SIZE - mineCount) + " safe tiles"));
            inventory.setItem(SLOT_MINES_PLUS, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 mine"));
        } else {
            for (int slot = 1; slot <= 8; slot++) {
                inventory.setItem(slot, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
            inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                    "§7Mines: " + mineCount + " (locked)"));
        }
    }

    private void renderGrid() {
        for (int index = 0; index < MinesGame.GRID_SIZE; index++) {
            inventory.setItem(gridSlot(index), buildTileIcon(index));
        }
        for (int slot : MARGIN_FILLER_SLOTS) {
            inventory.setItem(slot, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private ItemStack buildTileIcon(int index) {
        if (revealedSafe.contains(index)) {
            return GuiItems.named(Material.EMERALD, "§aSafe");
        }
        if (revealedMinesAfterRound.contains(index)) {
            boolean wasClicked = Integer.valueOf(index).equals(lastExplodedIndex);
            return wasClicked
                    ? GuiItems.named(Material.TNT, "§4§lBOOM", "§7You hit this one")
                    : GuiItems.named(Material.REDSTONE_BLOCK, "§cMine");
        }
        return GuiItems.named(Material.GRAY_STAINED_GLASS_PANE, "§7?", "§7Click to reveal");
    }

    private void renderStatus() {
        renderBalance();
        int safeTotal = MinesGame.GRID_SIZE - mineCount;
        inventory.setItem(SLOT_SAFE_COUNT, GuiItems.named(Material.EMERALD, "§aSafe found: " + revealedSafe.size() + " / " + safeTotal));

        if (roundActive) {
            long potentialPayout = Math.round(currentBet * cumulativeMultiplier);
            inventory.setItem(SLOT_POTENTIAL_PAYOUT, GuiItems.named(Material.GOLD_INGOT, "§ePotential: " + potentialPayout + " chips"));
            inventory.setItem(SLOT_MULTIPLIER, GuiItems.named(Material.NETHER_STAR, String.format("§eMultiplier: %.2fx", cumulativeMultiplier)));
            if (!revealedSafe.isEmpty()) {
                inventory.setItem(SLOT_CASH_OUT, GuiItems.named(Material.EMERALD_BLOCK, "§a§lCASH OUT", "§7Lock in " + potentialPayout + " chips"));
            } else {
                inventory.setItem(SLOT_CASH_OUT, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
        } else {
            inventory.setItem(SLOT_POTENTIAL_PAYOUT, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
            inventory.setItem(SLOT_MULTIPLIER, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
            inventory.setItem(SLOT_CASH_OUT, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    private ItemStack buildInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7Reveal tiles to raise your multiplier.");
        lore.add("§7Hit a mine and you lose the round.");
        lore.add("§7Cash out any time after your first");
        lore.add("§7safe tile to lock in your winnings.");
        lore.add(" ");
        lore.add("§7More mines = bigger multiplier per");
        lore.add("§7tile, but a much higher bust chance.");
        return GuiItems.named(Material.BOOK, "§6How to play", lore.toArray(new String[0]));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (!roundActive) {
            if (slot == SLOT_BET_MINUS_5) { adjustBet(-5); return; }
            if (slot == SLOT_BET_MINUS_1) { adjustBet(-1); return; }
            if (slot == SLOT_BET_PLUS_1) { adjustBet(1); return; }
            if (slot == SLOT_BET_PLUS_5) { adjustBet(5); return; }
            if (slot == SLOT_MINES_MINUS) { adjustMines(-1); return; }
            if (slot == SLOT_MINES_PLUS) { adjustMines(1); return; }
        }
        Integer tileIndex = SLOT_TO_TILE.get(slot);
        if (tileIndex != null) {
            handleTileClick(tileIndex);
            return;
        }
        if (roundActive && slot == SLOT_CASH_OUT && !revealedSafe.isEmpty()) {
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

    private void adjustMines(int delta) {
        mineCount = Math.max(MinesGame.MIN_MINES, Math.min(MinesGame.MAX_MINES, mineCount + delta));
        render();
    }

    private void handleTileClick(int index) {
        if (!roundActive) {
            startRound(index);
            return;
        }
        if (revealedSafe.contains(index)) {
            return; // already revealed, no-op
        }
        revealTile(index);
    }

    private void startRound(int firstTileIndex) {
        String denyReason = plugin.getBetLimitManager().checkBet(player, game.getId(), currentBet);
        if (denyReason != null) {
            player.sendMessage("§c" + denyReason);
            return;
        }
        if (!plugin.getEconomyManager().hasBalance(player, currentBet)) {
            player.sendMessage("§cYou don't have " + currentBet + " chips.");
            return;
        }
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
        revealedSafe = new HashSet<>();
        revealedMinesAfterRound = Set.of();
        lastExplodedIndex = null;
        minePositions = minesGame.generateMines(mineCount);

        revealTile(firstTileIndex);
    }

    private void revealTile(int index) {
        // Checked before every reveal, not just at round start — the streak has no fixed
        // payout ceiling, so this asks whether the bankroll, as it stands right now, could
        // cover the payout IF this reveal turns out safe. wager=0 since the bet itself was
        // already folded into the bankroll back in startRound().
        double nextMultiplier = cumulativeMultiplier * minesGame.stepMultiplier(mineCount, revealedSafe.size());
        long nextPotentialPayout = Math.round(currentBet * nextMultiplier);
        if (!plugin.getHouseBankroll().canAcceptBet(0, nextPotentialPayout)) {
            player.sendMessage("§cThe house can't cover a bigger win right now — cash out to lock in your current multiplier.");
            return;
        }

        if (minePositions.contains(index)) {
            bust(index);
            return;
        }

        cumulativeMultiplier = nextMultiplier;
        revealedSafe.add(index);

        if (revealedSafe.size() == MinesGame.GRID_SIZE - mineCount) {
            player.sendMessage("§a§lFull clear! §7Every safe tile found.");
            cashOut();
            return;
        }
        render();
    }

    private void bust(int index) {
        lastExplodedIndex = index;
        revealedMinesAfterRound = minePositions;
        plugin.getHouseBankroll().resolveBet(0);
        plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, 0, "LOSE");
        player.sendMessage("§c§lBOOM! §7You hit a mine and lost this round.");
        endRound();
    }

    private void cashOut() {
        long payout = Math.round(currentBet * cumulativeMultiplier);
        plugin.getEconomyManager().addChips(player, payout);
        plugin.getHouseBankroll().resolveBet(payout);
        plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, payout, "WIN");
        player.sendMessage("§a§lCashed out! §f+" + payout + " chips (" + String.format("%.2fx", cumulativeMultiplier) + ")");
        endRound();
    }

    private void endRound() {
        revealedMinesAfterRound = minePositions;
        roundActive = false;
        cumulativeMultiplier = 1.0;
        settled = true;
        render();
    }
}