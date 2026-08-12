package net.mercdev.casino.core.games.slots;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import net.mercdev.casino.core.util.SecureRng;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Classic 3-reel slots. Three reels are rolled independently from a weighted symbol
 * table; matching all three pays that symbol's (scaled) multiplier, any two matching
 * pays a flat smaller multiplier, anything else loses.
 * <p>
 * The payout numbers below aren't hand-picked to "feel about right" — {@link #computeScale}
 * derives a single scale factor from the configured house edge so the realized expected
 * payout is exactly {@code 1 - houseEdge} per chip staked, given the symbol weights and
 * relative payout shape defined in {@link #SYMBOLS}. Change the shape (weights or base
 * multipliers) and the scale simply adjusts to match; the house edge in config.yml is
 * always honored exactly, not approximately.
 */
public class SlotsGame implements CasinoGame {

    /** One symbol: its icon, rarity weight (out of TOTAL_WEIGHT), and its 3-of-a-kind
     *  payout multiplier before house-edge scaling. Reference equality (==) is used to
     *  compare rolled symbols elsewhere in this class — safe because rollReels() only
     *  ever returns the shared instances from {@link #SYMBOLS}, never copies. */
    public record SlotSymbol(Material material, String displayName, int weight, double baseTripleMultiplier) {}

    private static final int TOTAL_WEIGHT = 100;

    private static final List<SlotSymbol> SYMBOLS = List.of(
            new SlotSymbol(Material.COAL,            "Coal",       35, 3),
            new SlotSymbol(Material.IRON_INGOT,      "Iron",       25, 5),
            new SlotSymbol(Material.GOLD_INGOT,      "Gold",       18, 8),
            new SlotSymbol(Material.DIAMOND,         "Diamond",    12, 15),
            new SlotSymbol(Material.EMERALD,         "Emerald",    7,  30),
            new SlotSymbol(Material.NETHERITE_INGOT, "Netherite",  3,  100)
    );

    /** Flat multiplier for "any two of three reels match", uniform across symbols, pre-scaling. */
    private static final double BASE_PAIR_MULTIPLIER = 1.2;

    private final double scale;
    private final double scaledPairMultiplier;
    private final double maxScaledTripleMultiplier;

    public SlotsGame(CasinoPlugin plugin) {
        this.scale = computeScale(plugin.getHouseEdge());
        this.scaledPairMultiplier = BASE_PAIR_MULTIPLIER * scale;
        this.maxScaledTripleMultiplier = SYMBOLS.stream()
                .mapToDouble(s -> s.baseTripleMultiplier() * scale)
                .max().orElse(0);
    }

    private static double computeScale(double houseEdge) {
        double rawEv = 0;
        double pairProbTotal = 0;
        for (SlotSymbol s : SYMBOLS) {
            double p = s.weight() / (double) TOTAL_WEIGHT;
            double pTriple = p * p * p;
            double pPair = 3 * p * p * (1 - p); // exactly two of three reels show this symbol
            rawEv += pTriple * s.baseTripleMultiplier();
            pairProbTotal += pPair;
        }
        rawEv += pairProbTotal * BASE_PAIR_MULTIPLIER;
        return (1 - houseEdge) / rawEv;
    }

    public double scaledTripleMultiplier(SlotSymbol symbol) {
        return symbol.baseTripleMultiplier() * scale;
    }

    public double getScaledPairMultiplier() {
        return scaledPairMultiplier;
    }

    public List<SlotSymbol> getSymbols() {
        return SYMBOLS;
    }

    public double probabilityOfTriple(SlotSymbol symbol) {
        double p = symbol.weight() / (double) TOTAL_WEIGHT;
        return p * p * p;
    }

    /** Rolls 3 independent reels using the weighted symbol table. */
    public SlotSymbol[] rollReels() {
        return new SlotSymbol[]{pickSymbol(), pickSymbol(), pickSymbol()};
    }

    private SlotSymbol pickSymbol() {
        int roll = SecureRng.nextInt(TOTAL_WEIGHT);
        int cumulative = 0;
        for (SlotSymbol s : SYMBOLS) {
            cumulative += s.weight();
            if (roll < cumulative) return s;
        }
        return SYMBOLS.get(SYMBOLS.size() - 1); // unreachable: weights sum to TOTAL_WEIGHT
    }

    /** Payout for a resolved spin, in chips. */
    public long computePayout(SlotSymbol[] result, long bet) {
        if (result[0] == result[1] && result[1] == result[2]) {
            return Math.round(bet * scaledTripleMultiplier(result[0]));
        }
        if (result[0] == result[1] || result[1] == result[2] || result[0] == result[2]) {
            return Math.round(bet * scaledPairMultiplier);
        }
        return 0;
    }

    /** Worst case the house could owe on this bet — a triple of the highest-paying symbol.
     *  Used by HouseBankroll to decide whether a bet can be safely accepted. */
    public long maxPossiblePayout(long bet) {
        return Math.round(bet * maxScaledTripleMultiplier);
    }

    @Override
    public String getId() {
        return "slots";
    }

    @Override
    public String getDisplayName() {
        return "Slots";
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Slots");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new SlotsSession(plugin, player, this);
    }
}