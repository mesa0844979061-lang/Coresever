package com.kitduel;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public final class Kit {
    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<String> lore;
    /** ลำดับแบบ Bukkit: boots, leggings, chestplate, helmet */
    private ItemStack[] armor = new ItemStack[4];
    private ItemStack offhand;
    private final ItemStack[] storage = new ItemStack[36];

    public Kit(String id, String displayName, Material icon, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.lore = lore;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public List<String> lore() { return lore; }

    public Kit armor(ItemStack[] a) { this.armor = a; return this; }
    public Kit offhand(ItemStack i) { this.offhand = i; return this; }
    public Kit slot(int slot, ItemStack i) { storage[slot] = i; return this; }
    public Kit range(int from, int toInclusive, ItemStack i) {
        for (int s = from; s <= toInclusive && s < 36; s++) storage[s] = i.clone();
        return this;
    }

    /** ใส่ kit ให้ผู้เล่น (ล้างของเดิมก่อน) */
    public void apply(Player p) {
        p.closeInventory();
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] s = new ItemStack[36];
        for (int i = 0; i < 36; i++) s[i] = storage[i] == null ? null : storage[i].clone();
        inv.setStorageContents(s);
        ItemStack[] a = new ItemStack[4];
        for (int i = 0; i < 4; i++) a[i] = armor[i] == null ? null : armor[i].clone();
        inv.setArmorContents(a);
        inv.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
        inv.setHeldItemSlot(0);
        p.updateInventory();
    }
}
