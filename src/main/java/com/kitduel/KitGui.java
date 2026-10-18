package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KitGui {
    private KitGui() {}

    /** ช่องของ kit สูงสุด 21 ช่อง (กลางหน้าต่าง 3 แถว แถวละ 7) */
    public static final int[] KIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    public static final int CLOSE_SLOT = 49;

    public static final class Holder implements InventoryHolder {
        /** 1v1 = UUID ผู้เล่นที่ท้า, ปาตี้ = UUID ของปาตี้เป้าหมาย */
        public final UUID target;
        public final String targetName;
        public final boolean party;
        private Inventory inv;

        Holder(UUID target, String targetName, boolean party) {
            this.target = target;
            this.targetName = targetName;
            this.party = party;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public static int indexOf(int slot) {
        for (int i = 0; i < KIT_SLOTS.length; i++) if (KIT_SLOTS[i] == slot) return i;
        return -1;
    }

    private static ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(" ");
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    /** เปิดเมนู kit ท้าดวล 1v1 */
    public static void open(Player viewer, Player target, KitRegistry kits) {
        open(viewer, kits, target.getUniqueId(), target.getName(), false, target,
                "&e&l⚔ คู่ต่อสู้: &f&l" + target.getName(),
                List.of("&7เลือก kit ด้านล่างเพื่อส่งคำท้า",
                        "&7อีกฝ่ายต้องถือดาบแล้ว &fคลิกขวาที่คุณ",
                        "&7เพื่อรับคำท้า"),
                "&8&l⚔ &b&lเลือก KIT ท้าดวล &8&l⚔");
    }

    /** เปิดเมนู kit ท้าดวลปาตี้ (headOwner = หัวหน้าปาตี้เป้าหมาย) */
    public static void openParty(Player viewer, UUID partyId, String partyName, int size, Player headOwner,
                                 KitRegistry kits) {
        open(viewer, kits, partyId, partyName, true, headOwner,
                "&d&l⚔ ปาตี้คู่ต่อสู้: &f&l" + partyName,
                List.of("&7สมาชิก: &f" + size + " &7คน",
                        "&7เลือก kit ด้านล่างเพื่อส่งคำท้าปาตี้",
                        "&7หัวหน้าอีกฝ่ายต้องถือดาบปาตี้แล้ว",
                        "&fคลิกขวาที่คุณ &7เพื่อรับคำท้า"),
                "&8&l⚔ &d&lเลือก KIT ปาตี้ดวล &8&l⚔");
    }

    private static void open(Player viewer, KitRegistry kits, UUID key, String name, boolean party,
                             Player headOwner, String headName, List<String> headLore, String title) {
        Holder h = new Holder(key, name, party);
        Inventory inv = Bukkit.createInventory(h, 54, Msg.c(title));
        h.inv = inv;

        ItemStack edge = pane(party ? Material.MAGENTA_STAINED_GLASS_PANE : Material.CYAN_STAINED_GLASS_PANE);
        ItemStack inner = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack cornerPane = pane(party ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE);
        for (int s = 0; s < 54; s++) {
            int r = s / 9, c = s % 9;
            boolean corner = (r == 0 || r == 5) && (c == 0 || c == 8);
            boolean border = r == 0 || r == 5 || c == 0 || c == 8;
            inv.setItem(s, corner ? cornerPane : border ? edge : inner);
        }

        // หัวคู่ต่อสู้
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (headOwner != null) sm.setOwningPlayer(headOwner);
        sm.setDisplayName(Msg.c(headName));
        List<String> hl = new ArrayList<>();
        hl.add(Msg.c("&8&m                    "));
        for (String l : headLore) hl.add(Msg.c(l));
        sm.setLore(hl);
        sm.addItemFlags(ItemFlag.values());
        head.setItemMeta(sm);
        inv.setItem(4, head);

        // kit ทั้งหมด (สูงสุดเท่าจำนวนช่อง)
        List<Kit> list = kits.all();
        for (int i = 0; i < KIT_SLOTS.length && i < list.size(); i++) {
            Kit kit = list.get(i);
            ItemStack icon = new ItemStack(kit.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(Msg.c(kit.displayName()));
            List<String> lore = new ArrayList<>();
            lore.add(Msg.c("&8&m                    "));
            for (String l : kit.lore()) lore.add(Msg.c(l));
            lore.add(" ");
            lore.add(Msg.c("&e▶ คลิกเพื่อเลือกคิทนี้"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            icon.setItemMeta(meta);
            inv.setItem(KIT_SLOTS[i], icon);
        }

        // ปุ่มปิด
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(Msg.c("&c&l✖ ยกเลิก"));
        close.setItemMeta(cm);
        inv.setItem(CLOSE_SLOT, close);

        viewer.openInventory(inv);
        Msg.sound(viewer, Sound.BLOCK_CHEST_OPEN, 1.2f);
    }
}
