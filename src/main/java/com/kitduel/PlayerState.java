package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** เก็บสถานะผู้เล่นก่อนดวล (เซฟลงไฟล์ด้วย กันเซิร์ฟล่ม/ผู้เล่นหลุด ของจะไม่หาย) */
public final class PlayerState {
    private String worldName;
    private double x, y, z;
    private float yaw, pitch;
    private GameMode gameMode = GameMode.SURVIVAL;
    private double health = 20;
    private int food = 20;
    private float saturation = 5f, exhaustion = 0f;
    private int level;
    private float exp;
    private int fireTicks;
    private boolean allowFlight, flying;
    private final ItemStack[] storage = new ItemStack[36];
    private final ItemStack[] armor = new ItemStack[4];
    private ItemStack offhand;
    private final List<PotionEffect> effects = new ArrayList<>();

    public static PlayerState capture(Player p) {
        PlayerState s = new PlayerState();
        Location l = p.getLocation();
        s.worldName = l.getWorld().getName();
        s.x = l.getX();
        s.y = l.getY();
        s.z = l.getZ();
        s.yaw = l.getYaw();
        s.pitch = l.getPitch();
        s.gameMode = p.getGameMode();
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.saturation = p.getSaturation();
        s.exhaustion = p.getExhaustion();
        s.level = p.getLevel();
        s.exp = p.getExp();
        s.fireTicks = p.getFireTicks();
        s.allowFlight = p.getAllowFlight();
        s.flying = p.isFlying();
        PlayerInventory inv = p.getInventory();
        ItemStack[] st = inv.getStorageContents();
        for (int i = 0; i < 36 && i < st.length; i++) s.storage[i] = st[i] == null ? null : st[i].clone();
        ItemStack[] ar = inv.getArmorContents();
        for (int i = 0; i < 4 && i < ar.length; i++) s.armor[i] = ar[i] == null ? null : ar[i].clone();
        ItemStack off = inv.getItemInOffHand();
        s.offhand = off == null ? null : off.clone();
        s.effects.addAll(p.getActivePotionEffects());
        return s;
    }

    public Location location() {
        World w = worldName == null ? null : Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public void restore(Player p) {
        p.closeInventory();
        p.setItemOnCursor(null);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] st = new ItemStack[36];
        for (int i = 0; i < 36; i++) st[i] = storage[i] == null ? null : storage[i].clone();
        inv.setStorageContents(st);
        ItemStack[] ar = new ItemStack[4];
        for (int i = 0; i < 4; i++) ar[i] = armor[i] == null ? null : armor[i].clone();
        inv.setArmorContents(ar);
        inv.setItemInOffHand(offhand == null ? new ItemStack(org.bukkit.Material.AIR) : offhand.clone());
        p.setGameMode(gameMode);
        p.setAllowFlight(allowFlight);
        if (allowFlight) p.setFlying(flying);
        p.setAbsorptionAmount(0);
        p.setFireTicks(fireTicks);
        p.setFallDistance(0f);
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setExhaustion(exhaustion);
        p.setLevel(level);
        p.setExp(Math.max(0f, Math.min(0.999f, exp)));
        for (PotionEffect e : effects) p.addPotionEffect(e);
        double max = p.getMaxHealth();
        p.setHealth(Math.max(1.0, Math.min(health, max)));
        p.updateInventory();
    }

    public void save(File f) throws IOException {
        YamlConfiguration y = new YamlConfiguration();
        y.set("world", worldName);
        y.set("x", x);
        y.set("y", y0());
        y.set("z", z);
        y.set("yaw", (double) yaw);
        y.set("pitch", (double) pitch);
        y.set("gamemode", gameMode.name());
        y.set("health", health);
        y.set("food", food);
        y.set("saturation", (double) saturation);
        y.set("exhaustion", (double) exhaustion);
        y.set("level", level);
        y.set("exp", (double) exp);
        y.set("fire", fireTicks);
        y.set("allowFlight", allowFlight);
        y.set("flying", flying);
        for (int i = 0; i < 36; i++) if (storage[i] != null) y.set("storage." + i, storage[i]);
        for (int i = 0; i < 4; i++) if (armor[i] != null) y.set("armor." + i, armor[i]);
        if (offhand != null) y.set("offhand", offhand);
        y.set("effects", new ArrayList<>(effects));
        y.save(f);
    }

    private double y0() {
        return y;
    }

    public static PlayerState load(File f) {
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            PlayerState s = new PlayerState();
            s.worldName = y.getString("world");
            s.x = y.getDouble("x");
            s.y = y.getDouble("y");
            s.z = y.getDouble("z");
            s.yaw = (float) y.getDouble("yaw");
            s.pitch = (float) y.getDouble("pitch");
            try {
                s.gameMode = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
            } catch (IllegalArgumentException ex) {
                s.gameMode = GameMode.SURVIVAL;
            }
            s.health = y.getDouble("health", 20);
            s.food = y.getInt("food", 20);
            s.saturation = (float) y.getDouble("saturation", 5);
            s.exhaustion = (float) y.getDouble("exhaustion", 0);
            s.level = y.getInt("level");
            s.exp = (float) y.getDouble("exp");
            s.fireTicks = y.getInt("fire");
            s.allowFlight = y.getBoolean("allowFlight");
            s.flying = y.getBoolean("flying");
            ConfigurationSection sec = y.getConfigurationSection("storage");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < 36) s.storage[i] = sec.getItemStack(k);
                }
            }
            ConfigurationSection ar = y.getConfigurationSection("armor");
            if (ar != null) {
                for (String k : ar.getKeys(false)) {
                    int i = Integer.parseInt(k);
                    if (i >= 0 && i < 4) s.armor[i] = ar.getItemStack(k);
                }
            }
            s.offhand = y.getItemStack("offhand");
            List<?> list = y.getList("effects");
            if (list != null) {
                for (Object o : list) if (o instanceof PotionEffect pe) s.effects.add(pe);
            }
            return s;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[KitDuel] อ่านไฟล์สถานะไม่ได้: " + f.getName() + " -> " + ex);
            return null;
        }
    }
}
