package net.mercdev.casino.core.gui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/** Small helpers for building GUI item stacks without repeating ItemMeta boilerplate
 *  in every game's session class. */
public final class GuiItems {

    private GuiItems() {}

    public static ItemStack named(Material material, String displayName, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** A blank, unnamed filler item used to fill unused GUI slots (typically a glass pane). */
    public static ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Forces an item to show the enchantment glint, without needing a fake enchantment
     *  (and without the "hide the enchantment but keep the glint" dance that requires).
     *  Used to mark celebratory items — a jackpot reel, a revealed safe Mines tile — as
     *  visually distinct from an ordinary one. */
    public static ItemStack glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
