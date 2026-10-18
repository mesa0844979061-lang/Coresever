package com.kitduel;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.bukkit.enchantments.Enchantment.*;

/** รวม 13 kit: macepvp, macerocket, cpvp, cutpvp, uhc, smp, ffa, nethpot, diapot, axepvp, archer, sword, spearpvp */
public final class KitRegistry {
    private final List<Kit> kits = new ArrayList<>();

    public KitRegistry() {
        kits.add(mace());
        kits.add(maceRocket());
        kits.add(cpvp());
        kits.add(cutpvp());
        kits.add(uhc());
        kits.add(smp());
        kits.add(ffa());
        kits.add(nethpot());
        kits.add(diapot());
        kits.add(axepvp());
        kits.add(archer());
        kits.add(sword());
        kits.add(spear());
    }

    public List<Kit> all() {
        return Collections.unmodifiableList(kits);
    }

    // ---------- helpers ----------
    private static ItemStack i(Material m, int amt, Object... ench) {
        ItemStack it = new ItemStack(m, amt);
        if (ench.length > 0) {
            ItemMeta meta = it.getItemMeta();
            for (int k = 0; k + 1 < ench.length; k += 2) {
                // เอนชานต์ที่เซิร์ฟเวอร์เวอร์ชันนี้ไม่มี (null) ให้ข้าม ไม่ให้ kit พัง
                if (ench[k] == null) continue;
                meta.addEnchant((Enchantment) ench[k], (Integer) ench[k + 1], true);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack pot(Material m, PotionType t) {
        ItemStack it = new ItemStack(m, 1);
        PotionMeta pm = (PotionMeta) it.getItemMeta();
        pm.setBasePotionType(t);
        it.setItemMeta(pm);
        return it;
    }

    private static ItemStack splash(PotionType t) {
        return pot(Material.SPLASH_POTION, t);
    }

    private static ItemStack drink(PotionType t) {
        return pot(Material.POTION, t);
    }

    /** เกราะครบชุด: boots, leggings, chestplate, helmet */
    private static ItemStack[] armor(String type, int prot) {
        ItemStack[] r = new ItemStack[4];
        r[0] = i(Material.valueOf(type + "_BOOTS"), 1, PROTECTION, prot, UNBREAKING, 3, MENDING, 1, FEATHER_FALLING, 4);
        r[1] = i(Material.valueOf(type + "_LEGGINGS"), 1, PROTECTION, prot, UNBREAKING, 3, MENDING, 1);
        r[2] = i(Material.valueOf(type + "_CHESTPLATE"), 1, PROTECTION, prot, UNBREAKING, 3, MENDING, 1);
        r[3] = i(Material.valueOf(type + "_HELMET"), 1, PROTECTION, prot, UNBREAKING, 3, MENDING, 1);
        return r;
    }

    /** หาเอนชานต์จากชื่อ (null ถ้าเวอร์ชันเซิร์ฟเวอร์ไม่มี) */
    private static Enchantment ench(String key) {
        try {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        } catch (Throwable t) {
            return null;
        }
    }

    /** หาวัสดุจากชื่อ ถ้าเวอร์ชันนี้ไม่มี (เช่น หอกใน 1.21.11+) ใช้อันสำรองแทน */
    private static Material mat(String name, Material fallback) {
        Material m = Material.matchMaterial(name);
        return (m == null || m.isAir() || !m.isItem()) ? fallback : m;
    }

    /** จรวดพุ่ง (flight power) */
    private static ItemStack rocket(int power, int amt) {
        ItemStack it = new ItemStack(Material.FIREWORK_ROCKET, amt);
        FireworkMeta fm = (FireworkMeta) it.getItemMeta();
        fm.setPower(power);
        it.setItemMeta(fm);
        return it;
    }

    private static ItemStack totem() {
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }

    // ---------- kits ----------
    private static Kit mace() {
        Kit k = new Kit("macepvp", "&c&lMACE PVP", Material.MACE, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &cMace Density V &7+ Sword + Axe",
                "&7พิเศษ: &bWind Charge x64 &7+ Totem",
                "&7ของกิน: &6Enchanted Golden Apple"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(Material.MACE, 1, DENSITY, 5, WIND_BURST, 3, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(2, i(Material.NETHERITE_AXE, 1, SHARPNESS, 5, EFFICIENCY, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(3, i(Material.WIND_CHARGE, 64));
        k.slot(4, i(Material.ENDER_PEARL, 16));
        k.slot(5, i(Material.COBWEB, 64));
        k.slot(6, i(Material.ENCHANTED_GOLDEN_APPLE, 64));
        k.slot(7, i(Material.WATER_BUCKET, 1));
        k.slot(8, drink(PotionType.SLOW_FALLING));
        k.offhand(totem());
        k.range(9, 12, totem());
        k.range(13, 22, splash(PotionType.STRONG_HEALING));
        k.range(23, 25, drink(PotionType.STRONG_SWIFTNESS));
        k.slot(26, i(Material.WIND_CHARGE, 64));
        k.slot(27, i(Material.ENDER_PEARL, 16));
        k.slot(28, i(Material.COBWEB, 64));
        return k;
    }

    private static Kit cpvp() {
        Kit k = new Kit("cpvp", "&d&lCRYSTAL PVP", Material.END_CRYSTAL, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &cNetherite Sword Sharp V",
                "&7พิเศษ: &dEnd Crystal x256 &7+ Obsidian x256",
                "&7ของกิน: &6Enchanted Golden Apple x64 &7+ Totem x5"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.END_CRYSTAL, 64));
        k.slot(2, i(Material.OBSIDIAN, 64));
        k.slot(3, i(Material.ENCHANTED_GOLDEN_APPLE, 64));
        k.slot(4, i(Material.ENDER_PEARL, 16));
        k.slot(5, i(Material.EXPERIENCE_BOTTLE, 64));
        k.slot(6, i(Material.NETHERITE_PICKAXE, 1, EFFICIENCY, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(7, i(Material.GLOWSTONE, 64));
        k.slot(8, i(Material.COBWEB, 64));
        k.offhand(totem());
        k.range(9, 12, totem());
        k.slot(13, i(Material.END_CRYSTAL, 64));
        k.slot(14, i(Material.END_CRYSTAL, 64));
        k.slot(15, i(Material.OBSIDIAN, 64));
        k.slot(16, i(Material.OBSIDIAN, 64));
        k.slot(17, i(Material.EXPERIENCE_BOTTLE, 64));
        return k;
    }

    private static Kit cutpvp() {
        Kit k = new Kit("cutpvp", "&f&lCUT PVP", Material.IRON_SWORD, List.of(
                "&7เกราะ: &fIron Prot III",
                "&7อาวุธ: &fIron Sword Sharp III &7+ Bow Power III",
                "&7พิเศษ: &7น้ำ/ลาวา + Cobblestone + ไม้",
                "&7ของกิน: &6Golden Apple x16 &7+ เนื้อสุก x64"));
        k.armor(armor("IRON", 3));
        k.slot(0, i(Material.IRON_SWORD, 1, SHARPNESS, 3, UNBREAKING, 3));
        k.slot(1, i(Material.BOW, 1, POWER, 3, UNBREAKING, 3));
        k.slot(2, i(Material.COOKED_BEEF, 64));
        k.slot(3, i(Material.GOLDEN_APPLE, 16));
        k.slot(4, i(Material.WATER_BUCKET, 1));
        k.slot(5, i(Material.LAVA_BUCKET, 1));
        k.slot(6, i(Material.COBBLESTONE, 64));
        k.slot(7, i(Material.OAK_PLANKS, 64));
        k.slot(8, i(Material.IRON_PICKAXE, 1, EFFICIENCY, 3, UNBREAKING, 3));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3));
        k.slot(9, i(Material.ARROW, 32));
        k.slot(10, i(Material.FISHING_ROD, 1));
        k.slot(11, i(Material.IRON_AXE, 1, EFFICIENCY, 3, UNBREAKING, 3));
        k.slot(12, i(Material.WATER_BUCKET, 1));
        k.slot(13, i(Material.COBBLESTONE, 64));
        k.slot(14, i(Material.OAK_PLANKS, 64));
        return k;
    }

    private static Kit uhc() {
        Kit k = new Kit("uhc", "&6&lUHC", Material.GOLDEN_APPLE, List.of(
                "&7เกราะ: &bDiamond Prot III",
                "&7อาวุธ: &bDiamond Sword Sharp III &7+ Axe",
                "&7พิเศษ: &9Bow + Crossbow &7+ น้ำ x4 + ลาวา x2",
                "&7ของกิน: &6Golden Apple x16"));
        k.armor(armor("DIAMOND", 3));
        k.slot(0, i(Material.DIAMOND_SWORD, 1, SHARPNESS, 3, UNBREAKING, 3));
        k.slot(1, i(Material.BOW, 1, POWER, 3, UNBREAKING, 3));
        k.slot(2, i(Material.CROSSBOW, 1, QUICK_CHARGE, 2, UNBREAKING, 3));
        k.slot(3, i(Material.DIAMOND_AXE, 1, SHARPNESS, 3, EFFICIENCY, 3, UNBREAKING, 3));
        k.slot(4, i(Material.WATER_BUCKET, 1));
        k.slot(5, i(Material.LAVA_BUCKET, 1));
        k.slot(6, i(Material.COBBLESTONE, 64));
        k.slot(7, i(Material.OAK_PLANKS, 64));
        k.slot(8, i(Material.GOLDEN_APPLE, 16));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3));
        k.slot(9, i(Material.ARROW, 32));
        k.slot(10, i(Material.WATER_BUCKET, 1));
        k.slot(11, i(Material.WATER_BUCKET, 1));
        k.slot(12, i(Material.LAVA_BUCKET, 1));
        k.slot(13, i(Material.COBWEB, 8));
        k.slot(14, i(Material.DIAMOND_PICKAXE, 1, EFFICIENCY, 3, UNBREAKING, 3));
        k.slot(15, i(Material.COMPASS, 1));
        k.slot(16, i(Material.COBBLESTONE, 64));
        k.slot(17, i(Material.OAK_PLANKS, 64));
        return k;
    }

    private static Kit smp() {
        Kit k = new Kit("smp", "&a&lSMP", Material.NETHERITE_CHESTPLATE, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &cSword + Axe + Bow + Crossbow",
                "&7พิเศษ: &bElytra + Firework &7+ Pearl x16",
                "&7ของกิน: &6Enchanted Golden Apple x16"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, SWEEPING_EDGE, 3, LOOTING, 3, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.NETHERITE_AXE, 1, SHARPNESS, 5, EFFICIENCY, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(2, i(Material.NETHERITE_PICKAXE, 1, EFFICIENCY, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(3, i(Material.BOW, 1, POWER, 5, FLAME, 1, INFINITY, 1, UNBREAKING, 3));
        k.slot(4, i(Material.CROSSBOW, 1, QUICK_CHARGE, 3, PIERCING, 4, UNBREAKING, 3, MENDING, 1));
        k.slot(5, i(Material.ARROW, 64));
        k.slot(6, i(Material.ENCHANTED_GOLDEN_APPLE, 16));
        k.slot(7, i(Material.COOKED_BEEF, 64));
        k.slot(8, i(Material.ENDER_PEARL, 16));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3, MENDING, 1));
        k.slot(9, i(Material.FIREWORK_ROCKET, 64));
        k.slot(10, i(Material.ELYTRA, 1, UNBREAKING, 3, MENDING, 1));
        k.slot(11, totem());
        k.slot(12, i(Material.WATER_BUCKET, 1));
        k.slot(13, i(Material.LAVA_BUCKET, 1));
        k.slot(14, i(Material.COBWEB, 32));
        k.slot(15, i(Material.GOLDEN_CARROT, 64));
        k.slot(16, i(Material.OAK_PLANKS, 64));
        k.slot(17, i(Material.EXPERIENCE_BOTTLE, 64));
        return k;
    }

    private static Kit ffa() {
        Kit k = new Kit("ffa", "&e&lFFA", Material.DIAMOND_SWORD, List.of(
                "&7เกราะ: &bDiamond Prot III",
                "&7อาวุธ: &bDiamond Sword Sharp III &7+ Bow",
                "&7พิเศษ: &fCobweb + Pearl + เบ็ด",
                "&7ของกิน: &6Golden Apple x12"));
        k.armor(armor("DIAMOND", 3));
        k.slot(0, i(Material.DIAMOND_SWORD, 1, SHARPNESS, 3, FIRE_ASPECT, 1, UNBREAKING, 3));
        k.slot(1, i(Material.BOW, 1, POWER, 2, UNBREAKING, 3));
        k.slot(2, i(Material.FISHING_ROD, 1));
        k.slot(3, i(Material.GOLDEN_APPLE, 12));
        k.slot(4, i(Material.COOKED_BEEF, 32));
        k.slot(5, i(Material.ENDER_PEARL, 4));
        k.slot(6, i(Material.WATER_BUCKET, 1));
        k.slot(7, i(Material.COBWEB, 8));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3));
        k.slot(9, i(Material.ARROW, 32));
        return k;
    }

    private static Kit nethpot() {
        Kit k = new Kit("nethpot", "&5&lNETH POT", Material.NETHERITE_SWORD, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &cNetherite Sword Sharp V",
                "&7พิเศษ: &dSplash Healing x20 &7+ Strength + Speed",
                "&7ของกิน: &6Enchanted Golden Apple x64"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.ENDER_PEARL, 16));
        k.slot(2, i(Material.ENCHANTED_GOLDEN_APPLE, 64));
        k.slot(3, i(Material.COBWEB, 64));
        k.slot(4, i(Material.EXPERIENCE_BOTTLE, 64));
        k.slot(5, splash(PotionType.STRONG_HEALING));
        k.slot(6, splash(PotionType.STRONG_HEALING));
        k.slot(7, drink(PotionType.STRONG_STRENGTH));
        k.slot(8, drink(PotionType.STRONG_SWIFTNESS));
        k.offhand(totem());
        k.range(9, 26, splash(PotionType.STRONG_HEALING));
        k.range(27, 28, drink(PotionType.STRONG_SWIFTNESS));
        k.range(29, 30, drink(PotionType.STRONG_STRENGTH));
        k.slot(31, drink(PotionType.FIRE_RESISTANCE));
        k.range(32, 34, totem());
        return k;
    }

    private static Kit diapot() {
        Kit k = new Kit("diapot", "&b&lDIA POT", Material.DIAMOND_CHESTPLATE, List.of(
                "&7เกราะ: &bDiamond Prot IV",
                "&7อาวุธ: &bDiamond Sword Sharp V &7+ Trident",
                "&7พิเศษ: &dSplash Healing x15 &7+ Cobweb",
                "&7ของกิน: &6Golden Apple x64"));
        k.armor(armor("DIAMOND", 4));
        k.slot(0, i(Material.DIAMOND_SWORD, 1, SHARPNESS, 5, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.TRIDENT, 1, LOYALTY, 3, IMPALING, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(2, i(Material.DIAMOND_PICKAXE, 1, EFFICIENCY, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(3, i(Material.COBWEB, 64));
        k.slot(4, i(Material.OAK_PLANKS, 64));
        k.slot(5, i(Material.WATER_BUCKET, 1));
        k.slot(6, i(Material.ENDER_PEARL, 16));
        k.slot(7, i(Material.GOLDEN_APPLE, 64));
        k.slot(8, splash(PotionType.STRONG_HEALING));
        k.range(9, 22, splash(PotionType.STRONG_HEALING));
        k.range(23, 25, drink(PotionType.STRONG_SWIFTNESS));
        k.slot(26, drink(PotionType.STRONG_STRENGTH));
        k.slot(27, i(Material.COBWEB, 64));
        k.slot(28, i(Material.GOLDEN_APPLE, 64));
        return k;
    }

    private static Kit axepvp() {
        Kit k = new Kit("axepvp", "&4&lAXE PVP", Material.NETHERITE_AXE, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &cNetherite Axe Sharp V &7+ Sword",
                "&7พิเศษ: &8Shield &7+ Crossbow + Pearl",
                "&7ของกิน: &6Enchanted Golden Apple x32"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(Material.NETHERITE_AXE, 1, SHARPNESS, 5, EFFICIENCY, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, UNBREAKING, 3, MENDING, 1));
        k.slot(2, i(Material.CROSSBOW, 1, QUICK_CHARGE, 3, PIERCING, 3, UNBREAKING, 3, MENDING, 1));
        k.slot(3, i(Material.ARROW, 64));
        k.slot(4, i(Material.ENCHANTED_GOLDEN_APPLE, 32));
        k.slot(5, i(Material.ENDER_PEARL, 16));
        k.slot(6, i(Material.WATER_BUCKET, 1));
        k.slot(7, i(Material.COBWEB, 16));
        k.slot(8, i(Material.COOKED_BEEF, 64));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3, MENDING, 1));
        k.range(9, 11, totem());
        k.slot(12, i(Material.EXPERIENCE_BOTTLE, 64));
        return k;
    }

    private static Kit archer() {
        Kit k = new Kit("archer", "&2&lARCHER", Material.BOW, List.of(
                "&7เกราะ: &7Chainmail Prot III",
                "&7อาวุธ: &aBow Power V Punch II &7+ Crossbow",
                "&7พิเศษ: &dSplash Healing &7+ Pearl",
                "&7ของกิน: &6Golden Apple x16"));
        k.armor(armor("CHAINMAIL", 3));
        k.slot(0, i(Material.IRON_SWORD, 1, SHARPNESS, 2, KNOCKBACK, 1, UNBREAKING, 3));
        k.slot(1, i(Material.BOW, 1, POWER, 5, PUNCH, 2, FLAME, 1, UNBREAKING, 3));
        k.slot(2, i(Material.CROSSBOW, 1, QUICK_CHARGE, 3, MULTISHOT, 1, UNBREAKING, 3));
        k.slot(3, i(Material.ARROW, 64));
        k.slot(4, i(Material.ARROW, 64));
        k.slot(5, splash(PotionType.STRONG_HEALING));
        k.slot(6, i(Material.GOLDEN_APPLE, 16));
        k.slot(7, i(Material.ENDER_PEARL, 8));
        k.slot(8, drink(PotionType.STRONG_SWIFTNESS));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3));
        k.slot(9, i(Material.ARROW, 64));
        k.slot(10, i(Material.COOKED_BEEF, 64));
        k.range(11, 14, splash(PotionType.STRONG_HEALING));
        return k;
    }

    private static Kit maceRocket() {
        Kit k = new Kit("macerocket", "&6&lMACE ROCKET", Material.FIREWORK_ROCKET, List.of(
                "&7เกราะ: &fNetherite Prot IV &7+ &bElytra",
                "&7อาวุธ: &cMace Density V Wind Burst &7+ Sword",
                "&7พิเศษ: &6Firework Rocket x192 &7(บินสูงแล้วทุบ)",
                "&7ของกิน: &6Enchanted Golden Apple &7+ Totem"));
        ItemStack[] a = armor("NETHERITE", 4);
        // ช่องเกราะ: boots, leggings, chestplate, helmet -> เสื้อเป็น Elytra
        a[2] = i(Material.ELYTRA, 1, UNBREAKING, 3, MENDING, 1);
        k.armor(a);
        k.slot(0, i(Material.MACE, 1, DENSITY, 5, WIND_BURST, 3, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(2, rocket(3, 64));
        k.slot(3, rocket(3, 64));
        k.slot(4, i(Material.WIND_CHARGE, 64));
        k.slot(5, i(Material.ENDER_PEARL, 16));
        k.slot(6, i(Material.ENCHANTED_GOLDEN_APPLE, 64));
        k.slot(7, i(Material.WATER_BUCKET, 1));
        // เสื้อเกราะสำรอง: ถือแล้วคลิกขวาเพื่อสลับกับ Elytra ตอนลงพื้น
        k.slot(8, i(Material.NETHERITE_CHESTPLATE, 1, PROTECTION, 4, UNBREAKING, 3, MENDING, 1));
        k.offhand(totem());
        k.range(9, 12, totem());
        k.range(13, 18, splash(PotionType.STRONG_HEALING));
        k.slot(19, drink(PotionType.SLOW_FALLING));
        k.slot(20, drink(PotionType.SLOW_FALLING));
        k.range(21, 22, drink(PotionType.STRONG_SWIFTNESS));
        k.slot(23, rocket(3, 64));
        k.slot(24, i(Material.WIND_CHARGE, 64));
        k.slot(25, i(Material.COBWEB, 64));
        return k;
    }

    private static Kit sword() {
        Kit k = new Kit("sword", "&b&lSWORD PVP", Material.NETHERITE_SWORD, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &cNetherite Sword Sharp V Sweeping III",
                "&7พิเศษ: &8Shield &7+ Bow + Pearl x16",
                "&7ของกิน: &6Enchanted Golden Apple x32 &7+ Totem"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, SWEEPING_EDGE, 3, FIRE_ASPECT, 2, KNOCKBACK, 1,
                UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(Material.BOW, 1, POWER, 5, PUNCH, 1, UNBREAKING, 3, MENDING, 1));
        k.slot(2, i(Material.ARROW, 64));
        k.slot(3, i(Material.ENCHANTED_GOLDEN_APPLE, 32));
        k.slot(4, i(Material.ENDER_PEARL, 16));
        k.slot(5, i(Material.COBWEB, 16));
        k.slot(6, i(Material.WATER_BUCKET, 1));
        k.slot(7, i(Material.COOKED_BEEF, 64));
        k.slot(8, drink(PotionType.STRONG_SWIFTNESS));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3, MENDING, 1));
        k.range(9, 12, totem());
        k.range(13, 16, splash(PotionType.STRONG_HEALING));
        k.slot(17, drink(PotionType.STRONG_STRENGTH));
        k.slot(18, i(Material.ARROW, 64));
        k.slot(19, i(Material.EXPERIENCE_BOTTLE, 64));
        return k;
    }

    private static Kit spear() {
        // หอกมีใน Minecraft 1.21.11 ขึ้นไป — ถ้าเซิร์ฟเวอร์เวอร์ชันเก่ากว่านั้นจะใช้ Trident แทนอัตโนมัติ
        Material spearMat = mat("NETHERITE_SPEAR", Material.TRIDENT);
        boolean realSpear = spearMat != Material.TRIDENT;
        Enchantment lunge = ench("lunge");
        Kit k = new Kit("spearpvp", "&3&lSPEAR PVP", spearMat, List.of(
                "&7เกราะ: &fNetherite Prot IV",
                "&7อาวุธ: &bNetherite Spear" + (realSpear ? " Lunge III" : " &8(เวอร์ชันนี้ไม่มีหอก ใช้ Trident แทน)")
                        + " &7+ Sword",
                "&7พิเศษ: &8Shield &7+ Pearl x16 &7+ Cobweb",
                "&7ของกิน: &6Enchanted Golden Apple x32 &7+ Totem"));
        k.armor(armor("NETHERITE", 4));
        k.slot(0, i(spearMat, 1, lunge, 3, UNBREAKING, 3, MENDING, 1));
        k.slot(1, i(spearMat, 1, lunge, 3, UNBREAKING, 3, MENDING, 1)); // สำรอง (กรณีหอกพัง/ทิ้ง)
        k.slot(2, i(Material.NETHERITE_SWORD, 1, SHARPNESS, 5, FIRE_ASPECT, 2, UNBREAKING, 3, MENDING, 1));
        k.slot(3, i(Material.ENCHANTED_GOLDEN_APPLE, 32));
        k.slot(4, i(Material.ENDER_PEARL, 16));
        k.slot(5, i(Material.COBWEB, 16));
        k.slot(6, i(Material.WATER_BUCKET, 1));
        k.slot(7, i(Material.COOKED_BEEF, 64));
        k.slot(8, drink(PotionType.STRONG_SWIFTNESS));
        k.offhand(i(Material.SHIELD, 1, UNBREAKING, 3, MENDING, 1));
        k.range(9, 12, totem());
        k.range(13, 16, splash(PotionType.STRONG_HEALING));
        k.slot(17, drink(PotionType.STRONG_STRENGTH));
        return k;
    }
}
