package net.mercdev.casino.core.games.roulette;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A full roulette table in one 54-slot inventory: row 0 is controls, rows 1-4 are the
 * number grid (1-36, 9 per row), row 5 is the outside bets. Clicking any bet target places
 * the current bet amount on it and spins immediately — like Slots, every click resolves
 * atomically, so onClose never needs to refund anything.
 * <p>
 * The number grid and outside-bet row are built once in open() and never rebuilt (their
 * lore doesn't depend on the current bet amount, deliberately, so adjusting the bet
 * doesn't require re-rendering 46 items); only row 0 (info/controls/balance) re-renders
 * after each bet-adjust or spin.
 */
public class RouletteSession extends GameSession {

    private static final int SLOT_INFO = 0;
    private static final int SLOT_BET_MINUS_5 = 1;
    private static final int SLOT_BET_MINUS_1 = 2;
    private static final int SLOT_BET_DISPLAY = 3;
    private static final int SLOT_BET_PLUS_1 = 4;
    private static final int SLOT_BET_PLUS_5 = 5;
    private static final int SLOT_ZERO = 6;
    private static final int SLOT_FILLER = 7;
    private static final int SLOT_BALANCE = 8;
    private static final int FIRST_NUMBER_SLOT = 9;  // numbers 1..36 occupy 9..44
    private static final int FIRST_OUTSIDE_SLOT = 45; // 45..53, in OutsideBet enum order

    private final RouletteGame rouletteGame;
    private final Map<Integer, Integer> slotToStraightNumber = new HashMap<>();
    private final Map<Integer, RouletteGame.OutsideBet> slotToOutsideBet = new HashMap<>();
    private long currentBet;
    private Integer lastResult;

    protected RouletteSession(CasinoPlugin plugin, Player player, RouletteGame game) {
        super(plugin, player, game);
        this.rouletteGame = game;
        this.currentBet = plugin.getBetLimitManager().getMinBet(game.getId());
    }

    @Override
    public void open() {
        this.inventory = Bukkit.createInventory(this, 54, "Roulette");
        renderGrid();
        renderControls();
        player.openInventory(inventory);
    }

    private void renderGrid() {
        slotToStraightNumber.put(SLOT_ZERO, 0);
        inventory.setItem(SLOT_ZERO, buildNumberIcon(0));

        for (int number = 1; number <= 36; number++) {
            int slot = FIRST_NUMBER_SLOT + (number - 1);
            slotToStraightNumber.put(slot, number);
            inventory.setItem(slot, buildNumberIcon(number));
        }

        for (RouletteGame.OutsideBet betType : RouletteGame.OutsideBet.values()) {
            int slot = FIRST_OUTSIDE_SLOT + betType.ordinal();
            slotToOutsideBet.put(slot, betType);
            inventory.setItem(slot, buildOutsideBetIcon(betType));
        }
    }

    private ItemStack buildNumberIcon(int number) {
        Material material = number == 0 ? Material.LIME_CONCRETE
                : rouletteGame.isRed(number) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        String label = number == 0 ? "§a0" : rouletteGame.isRed(number) ? "§c" + number : "§f" + number;
        return GuiItems.named(material, label, "§7Straight bet (36x) — click to spin");
    }

    private ItemStack buildOutsideBetIcon(RouletteGame.OutsideBet betType) {
        record Visual(Material material, String label) {}
        Visual visual = switch (betType) {
            case RED -> new Visual(Material.RED_CONCRETE, "§c§lRED");
            case BLACK -> new Visual(Material.BLACK_CONCRETE, "§8§lBLACK");
            case ODD -> new Visual(Material.IRON_NUGGET, "§fODD");
            case EVEN -> new Visual(Material.GOLD_NUGGET, "§fEVEN");
            case LOW -> new Visual(Material.COAL, "§f1-18");
            case HIGH -> new Visual(Material.DIAMOND, "§f19-36");
            case DOZEN_1 -> new Visual(Material.LIME_DYE, "§a1st 12 §7(1-12)");
            case DOZEN_2 -> new Visual(Material.YELLOW_DYE, "§e2nd 12 §7(13-24)");
            case DOZEN_3 -> new Visual(Material.PURPLE_DYE, "§d3rd 12 §7(25-36)");
        };
        String lore = betType.name().startsWith("DOZEN") ? "§7Pays 3x — click to spin" : "§7Pays 2x — click to spin";
        return GuiItems.named(visual.material(), visual.label(), lore);
    }

    private void renderControls() {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());

        inventory.setItem(SLOT_INFO, buildInfoItem());
        inventory.setItem(SLOT_BET_MINUS_5, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-5 bet"));
        inventory.setItem(SLOT_BET_MINUS_1, GuiItems.named(Material.RED_STAINED_GLASS_PANE, "§c-1 bet"));
        inventory.setItem(SLOT_BET_DISPLAY, GuiItems.named(Material.PAPER, "§eBet: " + currentBet + " chips",
                "§7Min " + min + " / Max " + max));
        inventory.setItem(SLOT_BET_PLUS_1, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+1 bet"));
        inventory.setItem(SLOT_BET_PLUS_5, GuiItems.named(Material.LIME_STAINED_GLASS_PANE, "§a+5 bet"));
        inventory.setItem(SLOT_FILLER, GuiItems.filler(Material.PURPLE_TERRACOTTA));
        renderBalance();
    }

    private void renderBalance() {
        long balance = plugin.getEconomyManager().getBalance(player);
        inventory.setItem(SLOT_BALANCE, GuiItems.named(Material.GOLD_INGOT, "§6Balance: " + balance + " chips"));
    }

    private ItemStack buildInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("§7European roulette, single 0.");
        lore.add("§7House edge: §f2.70% §7(fixed by the wheel,");
        lore.add("§7not the config house-edge value).");
        lore.add(" ");
        lore.add("§fStraight number: §a36x");
        lore.add("§fDozens: §a3x");
        lore.add("§fRed/Black/Odd/Even/1-18/19-36: §a2x");
        if (lastResult != null) {
            lore.add(" ");
            String colour = lastResult == 0 ? "§aGreen" : rouletteGame.isRed(lastResult) ? "§cRed" : "§8Black";
            lore.add("§7Last result: " + colour + " §f" + lastResult);
        }
        return GuiItems.named(Material.BOOK, "§6Payout Table", lore.toArray(new String[0]));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == SLOT_BET_MINUS_5) { adjustBet(-5); return; }
        if (slot == SLOT_BET_MINUS_1) { adjustBet(-1); return; }
        if (slot == SLOT_BET_PLUS_1) { adjustBet(1); return; }
        if (slot == SLOT_BET_PLUS_5) { adjustBet(5); return; }

        Integer straightNumber = slotToStraightNumber.get(slot);
        if (straightNumber != null) {
            resolveStraightBet(straightNumber);
            return;
        }
        RouletteGame.OutsideBet outsideBet = slotToOutsideBet.get(slot);
        if (outsideBet != null) {
            resolveOutsideBet(outsideBet);
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Every spin resolves fully inside a single click — nothing is ever left mid-bet.
        settled = true;
    }

    private void adjustBet(long delta) {
        long min = plugin.getBetLimitManager().getMinBet(game.getId());
        long max = plugin.getBetLimitManager().getMaxBet(game.getId());
        currentBet = Math.max(min, Math.min(max, currentBet + delta));
        renderControls();
    }

    /** Runs bet-limit/balance/bankroll checks, deducts the bet, and reserves it against
     *  the house bankroll. Returns false (and messages the player) if it can't be accepted. */
    private boolean placeBet(long maxPayout) {
        String denyReason = plugin.getBetLimitManager().checkBet(player, game.getId(), currentBet);
        if (denyReason != null) {
            player.sendMessage("§c" + denyReason);
            return false;
        }
        if (!plugin.getEconomyManager().hasBalance(player, currentBet)) {
            player.sendMessage("§cYou don't have " + currentBet + " chips.");
            return false;
        }
        if (!plugin.getHouseBankroll().canAcceptBet(currentBet, maxPayout)) {
            player.sendMessage("§cThe house can't cover a potential payout on this bet right now — try lowering it.");
            return false;
        }
        plugin.getEconomyManager().removeChips(player, currentBet);
        plugin.getHouseBankroll().reserveBet(currentBet);
        GameFx.lever(player);
        return true;
    }

    private void resolveStraightBet(int number) {
        if (!placeBet(rouletteGame.straightPayout(currentBet))) return;
        int result = rouletteGame.spin();
        boolean win = result == number;
        long payout = win ? rouletteGame.straightPayout(currentBet) : 0;
        settle(result, win, payout, "Straight " + number);
    }

    private void resolveOutsideBet(RouletteGame.OutsideBet betType) {
        if (!placeBet(rouletteGame.outsidePayout(betType, currentBet))) return;
        int result = rouletteGame.spin();
        boolean win = rouletteGame.outsideBetWins(betType, result);
        long payout = win ? rouletteGame.outsidePayout(betType, currentBet) : 0;
        settle(result, win, payout, betType.name());
    }

    private void settle(int result, boolean win, long payout, String betDescription) {
        if (payout > 0) {
            plugin.getEconomyManager().addChips(player, payout);
        }
        plugin.getHouseBankroll().resolveBet(payout);
        plugin.getAuditLogger().logBet(player.getUniqueId(), game.getId(), currentBet, payout, win ? "WIN" : "LOSE");
        plugin.getBetLimitManager().recordBet(player);

        lastResult = result;
        renderControls();
        int resultSlot = (result == 0) ? SLOT_ZERO : FIRST_NUMBER_SLOT + (result - 1);
        inventory.setItem(resultSlot, GuiItems.glow(buildNumberIcon(result)));

        String colour = result == 0 ? "§aGreen" : rouletteGame.isRed(result) ? "§cRed" : "§8Black";
        if (win) {
            boolean bigWin = payout >= currentBet * 10; // straight-up hit
            if (bigWin) {
                GameFx.jackpot(player);
            } else {
                GameFx.win(player);
            }
            player.sendMessage("§a§lWIN! §fThe ball landed on " + colour + "§f " + result
                    + " — you won " + payout + " chips (" + betDescription + ").");
        } else {
            GameFx.lose(player);
            player.sendMessage("§7The ball landed on " + colour + "§7 " + result
                    + ". No win (" + betDescription + ").");
        }
    }
}
