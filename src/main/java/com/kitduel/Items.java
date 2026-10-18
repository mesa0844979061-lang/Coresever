package com.kitduel;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public final class Items {
    private Items() {}

    private static NamespacedKey key;
    private static NamespacedKey partyKey;

    public static void init(KitDuelPlugin plugin) {
        key = new NamespacedKey(plugin, "duel_sword");
        partyKey = new NamespacedKey(plugin, "party_sword");
    }

    private static Material material(KitDuelPlugin plugin, String path, Material def) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path, def.name()));
        if (m == null || m.isAir() || !m.isItem()) m = def;
        return m;
    }

    // ------------------------------------------------------------------ duel sword (1v1)

    public static ItemStack duelSword(KitDuelPlugin plugin) {
        ItemStack it = new ItemStack(material(plugin, "sword-material", Material.DIAMOND_SWORD));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Msg.c("&b&l⚔ &f&lดาบท้าดวล &b&l⚔"));
        meta.setLore(List.of(
                Msg.c("&8&m                    "),
                Msg.c("&7▸ &fคลิกขวาที่ผู้เล่น &7เพื่อท้าดวล"),
                Msg.c("&7▸ &fคลิกขวาที่คนท้า &7เพื่อรับคำท้า"),
                Msg.c("&8&m                    ")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.values());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isDuelSword(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static boolean hasSword(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isDuelSword(it)) return true;
        }
        return false;
    }

    /** ให้ดาบถ้ายังไม่มี คืน true ถ้าได้ให้ */
    public static boolean ensureSword(KitDuelPlugin plugin, Player p) {
        if (hasSword(p)) return false;
        give(p, duelSword(plugin));
        return true;
    }

    public static void giveSword(KitDuelPlugin plugin, Player p) {
        give(p, duelSword(plugin));
    }

    // ------------------------------------------------------------------ party sword

    public static ItemStack partySword(KitDuelPlugin plugin) {
        ItemStack it = new ItemStack(material(plugin, "party-sword-material", Material.NETHERITE_SWORD));
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Msg.c("&d&l⚔ &f&lดาบปาตี้ดวล &d&l⚔"));
        meta.setLore(List.of(
                Msg.c("&8&m                    "),
                Msg.c("&7▸ &fคลิกขวาผู้เล่นที่ไม่มีปาตี้ &7เพื่อเชิญ"),
                Msg.c("&7▸ &fคลิกขวาผู้เล่นปาตี้อื่น &7เพื่อท้าดวลปาตี้"),
                Msg.c("&7▸ &fคลิกขวาหัวหน้าที่ท้า &7เพื่อรับคำท้า"),
                Msg.c("&8▸ &7เฉพาะหัวหน้าปาตี้เท่านั้น"),
                Msg.c("&8&m                    ")));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.values());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.getPersistentDataContainer().set(partyKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isPartySword(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(partyKey, PersistentDataType.BYTE);
    }

    /** ดาบเมนูของปลั๊กอิน (ห้ามใช้ตีคน/ห้ามทิ้ง) */
    public static boolean isMenuSword(ItemStack it) {
        return isDuelSword(it) || isPartySword(it);
    }

    public static boolean hasPartySword(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isPartySword(it)) return true;
        }
        return false;
    }

    public static void givePartySword(KitDuelPlugin plugin, Player p) {
        if (!hasPartySword(p)) give(p, partySword(plugin));
    }

    public static void removePartySwords(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < c.length; i++) {
            if (isPartySword(c[i])) {
                c[i] = null;
                changed = true;
            }
        }
        if (changed) {
            p.getInventory().setContents(c);
            p.updateInventory();
        }
    }

    private static void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack it : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), it);
        }
    }
}
