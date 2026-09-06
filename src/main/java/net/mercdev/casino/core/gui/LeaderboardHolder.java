package net.mercdev.casino.core.gui;

import net.mercdev.casino.core.CasinoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A read-only "richest players" display. Deliberately not a CasinoGame/GameSession — it
 * doesn't bet, doesn't mutate the economy, and the hub's game-icon row is already full
 * (7 slots, 7 games) — so it's just a plain InventoryHolder with its own hub button and
 * its own (trivial) click routing in CasinoGuiListener.
 * <p>
 * Ranking uses AuditLogger's DB snapshot for every OTHER player, but the viewer's own
 * balance comes from EconomyManager's live cache instead of that same snapshot — the
 * snapshot can lag a little behind since balance saves are async, and that lag is far
 * more likely to be stale for "the player who just opened this menu" than for anyone
 * else. The viewer's entry is folded into the sorted list before ranking, so it's always
 * shown (and ranked) using their true current balance, whether or not they make the cut.
 */
public class LeaderboardHolder implements InventoryHolder {

    private static final int SLOT_INFO = 4;
    private static final int FIRST_ENTRY_SLOT = 9;
    private static final int LAST_ENTRY_SLOT = 52; // slot 53 reserved for the viewer's own entry, if not shown above
    private static final int YOUR_RANK_SLOT = 53;

    private final Inventory inventory;

    public LeaderboardHolder(CasinoPlugin plugin, Player viewer, int size) {
        this.inventory = Bukkit.createInventory(this, 54, "Leaderboard");
        build(plugin, viewer, size);
    }

    private void build(CasinoPlugin plugin, Player viewer, int size) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, GuiItems.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(SLOT_INFO, GuiItems.named(Material.BOOK, "§6Leaderboard",
                "§7The " + size + " richest players on the server."));

        long viewerBalance = plugin.getEconomyManager().getBalance(viewer);
        List<Map.Entry<UUID, Long>> others = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : plugin.getAuditLogger().getAllBalancesDescending()) {
            if (!entry.getKey().equals(viewer.getUniqueId())) {
                others.add(entry);
            }
        }

        List<Map.Entry<UUID, Long>> combined = new ArrayList<>(others);
        combined.add(Map.entry(viewer.getUniqueId(), viewerBalance));
        combined.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        int maxListSlots = LAST_ENTRY_SLOT - FIRST_ENTRY_SLOT + 1;
        int shown = Math.min(size, Math.min(combined.size(), maxListSlots));

        int slot = FIRST_ENTRY_SLOT;
        boolean viewerShown = false;
        for (int i = 0; i < shown; i++) {
            Map.Entry<UUID, Long> entry = combined.get(i);
            int rank = i + 1;
            boolean isViewer = entry.getKey().equals(viewer.getUniqueId());
            viewerShown |= isViewer;
            inventory.setItem(slot++, buildEntryIcon(rank, entry.getKey(), entry.getValue(), isViewer));
        }

        if (!viewerShown) {
            int rank = 1;
            for (Map.Entry<UUID, Long> entry : combined) {
                if (entry.getKey().equals(viewer.getUniqueId())) break;
                rank++;
            }
            inventory.setItem(YOUR_RANK_SLOT, buildEntryIcon(rank, viewer.getUniqueId(), viewerBalance, true));
        }
    }

    private ItemStack buildEntryIcon(int rank, UUID uuid, long balance, boolean isViewer) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) name = "Unknown";

        Material material = rank == 1 ? Material.GOLD_INGOT : rank <= 3 ? Material.IRON_INGOT : Material.PAPER;
        String rankColour = rank == 1 ? "§6" : rank <= 3 ? "§7" : "§f";
        String label = isViewer
                ? "§a#" + rank + " §f" + name + " §7(you)"
                : rankColour + "#" + rank + " §f" + name;

        ItemStack icon = GuiItems.named(material, label, "§7Balance: §f" + balance + " chips");
        return isViewer ? GuiItems.glow(icon) : icon;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}