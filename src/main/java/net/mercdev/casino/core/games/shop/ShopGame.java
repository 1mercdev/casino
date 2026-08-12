package net.mercdev.casino.core.games.shop;

import net.mercdev.casino.core.CasinoPlugin;
import net.mercdev.casino.core.game.CasinoGame;
import net.mercdev.casino.core.game.GameSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A chip-spending catalog, not a game — there's no bet, no win/loss, no house bankroll
 * involvement. It implements CasinoGame anyway because that's what gets it a hub icon and
 * click routing through the existing framework for free; a purchase resolves atomically
 * on click, the same way a Slots spin does, so there's nothing to settle or refund.
 * <p>
 * The catalog is read from config.yml's "shop.items" list rather than hardcoded, so items
 * can be added/repriced without a rebuild. See config.yml for the expected shape.
 */
public class ShopGame implements CasinoGame {

    public record ShopItem(String id, Material material, String displayName, long price,
                            int amount, String permission, List<String> lore) {}

    private final List<ShopItem> catalog;

    public ShopGame(CasinoPlugin plugin) {
        this.catalog = loadCatalog(plugin);
    }

    public List<ShopItem> getCatalog() {
        return catalog;
    }

    private static List<ShopItem> loadCatalog(CasinoPlugin plugin) {
        List<ShopItem> items = new ArrayList<>();
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("shop.items");

        for (Map<?, ?> entry : raw) {
            Object idObj = entry.get("id");
            String id = idObj != null ? String.valueOf(idObj) : null;
            if (id == null || id.isBlank()) {
                plugin.getLogger().warning("Shop item missing 'id', skipping.");
                continue;
            }

            String materialName = entry.get("material") != null ? String.valueOf(entry.get("material")) : "";
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                plugin.getLogger().warning("Shop item '" + id + "' has invalid material '" + materialName + "', skipping.");
                continue;
            }

            long price = entry.get("price") instanceof Number n ? n.longValue() : -1;
            if (price <= 0) {
                plugin.getLogger().warning("Shop item '" + id + "' has an invalid price, skipping.");
                continue;
            }

            String name = entry.get("name") != null ? String.valueOf(entry.get("name")) : prettify(material.name());
            int amount = entry.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
            String permission = entry.get("permission") != null ? String.valueOf(entry.get("permission")) : null;

            List<String> lore = new ArrayList<>();
            if (entry.get("lore") instanceof List<?> loreLines) {
                for (Object line : loreLines) {
                    lore.add(String.valueOf(line));
                }
            }

            items.add(new ShopItem(id, material, name, price, amount, permission, lore));
        }
        return items;
    }

    private static String prettify(String enumName) {
        StringBuilder sb = new StringBuilder();
        for (String part : enumName.toLowerCase().split("_")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @Override
    public String getId() {
        return "shop";
    }

    @Override
    public String getDisplayName() {
        return "Shop";
    }

    @Override
    public ItemStack getMenuIcon() {
        ItemStack icon = new ItemStack(Material.EMERALD);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aShop");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public GameSession createSession(CasinoPlugin plugin, Player player) {
        return new ShopSession(plugin, player, this);
    }
}