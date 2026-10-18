package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KitDuelPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {
    private DuelManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Items.init(this);
        KitRegistry kits = new KitRegistry();
        manager = new DuelManager(this, kits);
        manager.start();
        Bukkit.getPluginManager().registerEvents(new DuelListener(this, manager), this);
        if (getCommand("duel") != null) {
            getCommand("duel").setExecutor(this);
            getCommand("duel").setTabCompleter(this);
        }
        if (getCommand("party") != null) {
            PartyCommand pc = new PartyCommand(this, manager);
            getCommand("party").setExecutor(pc);
            getCommand("party").setTabCompleter(pc);
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            getLogger().warning("ไม่พบ Multiverse-Core — ต้องมีโลก '" + getConfig().getString("pvp-world", "pvp")
                    + "' โหลดอยู่แล้ว มิฉะนั้นดวลไม่ได้");
        }
        getLogger().info("KitDuel เปิดใช้งาน — " + kits.all().size() + " kits");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.LINE);
        s.sendMessage(Msg.c("&b&l  KitDuel &7— ระบบท้าดวลเลือก Kit"));
        s.sendMessage(Msg.c("&7  ▸ &fคลิกขวาผู้เล่น &7ด้วยดาบท้าดวล → เลือก Kit"));
        s.sendMessage(Msg.c("&7  ▸ อีกฝ่ายถือดาบ &fคลิกขวาที่คนท้า &7เพื่อรับ"));
        s.sendMessage(Msg.c("&e  /duel sword &7— รับดาบท้าดวล"));
        s.sendMessage(Msg.c("&e  /duel leave &7— ยอมแพ้/ออกจากดวล"));
        s.sendMessage(Msg.c("&d  /party &7— ปาตี้ดวลทีมมากกว่า 2 คน (&f/party help&7)"));
        if (s.hasPermission("kitduel.admin")) {
            s.sendMessage(Msg.c("&c  /duel sword <ผู้เล่น> &7— ให้ดาบผู้อื่น"));
            s.sendMessage(Msg.c("&c  /duel setspawn <สนาม> <1|2> &7— ตั้งจุดเกิด"));
            s.sendMessage(Msg.c("&c  /duel delarena <สนาม> &7| &c/duel arenas &7| &c/duel reload"));
        }
        s.sendMessage(Msg.LINE);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "sword" -> {
                Player target;
                if (a.length >= 2) {
                    if (!s.hasPermission("kitduel.admin")) {
                        Msg.send(s, "&cไม่มีสิทธิ์ให้ดาบผู้อื่น");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(a[1]);
                    if (target == null) {
                        Msg.send(s, "&cไม่พบผู้เล่น " + a[1]);
                        return true;
                    }
                } else if (s instanceof Player p) {
                    target = p;
                } else {
                    Msg.send(s, "&c/duel sword <ผู้เล่น>");
                    return true;
                }
                if (manager.isBusy(target)) {
                    Msg.send(s, "&cผู้เล่นกำลังอยู่ในดวล");
                    return true;
                }
                if (Items.ensureSword(this, target)) {
                    Msg.send(target, "&aได้รับ &b&lดาบท้าดวล &aแล้ว!");
                    if (!target.equals(s)) Msg.send(s, "&aให้ดาบแก่ &f" + target.getName() + " &aแล้ว");
                } else {
                    Msg.send(s, "&e" + target.getName() + " มีดาบท้าดวลอยู่แล้ว");
                }
            }
            case "leave" -> {
                if (!(s instanceof Player p)) {
                    Msg.send(s, "&cใช้ได้เฉพาะผู้เล่น");
                    return true;
                }
                manager.forfeit(p);
            }
            case "setspawn" -> {
                if (!s.hasPermission("kitduel.admin")) {
                    Msg.send(s, "&cไม่มีสิทธิ์");
                    return true;
                }
                if (!(s instanceof Player p)) {
                    Msg.send(s, "&cใช้ได้เฉพาะผู้เล่น");
                    return true;
                }
                if (a.length < 3 || !(a[2].equals("1") || a[2].equals("2"))) {
                    Msg.send(s, "&c/duel setspawn <ชื่อสนาม> <1|2>");
                    return true;
                }
                String name = a[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "");
                if (name.isEmpty()) {
                    Msg.send(s, "&cชื่อสนามไม่ถูกต้อง (ใช้ a-z 0-9 _ -)");
                    return true;
                }
                Location l = p.getLocation();
                getConfig().set("arenas." + name + ".spawn" + a[2], DuelManager.formatLoc(l));
                saveConfig();
                Msg.send(s, "&aตั้ง &fspawn" + a[2] + " &aของสนาม &e" + name + " &aแล้ว");
            }
            case "delarena" -> {
                if (!s.hasPermission("kitduel.admin")) {
                    Msg.send(s, "&cไม่มีสิทธิ์");
                    return true;
                }
                if (a.length < 2) {
                    Msg.send(s, "&c/duel delarena <ชื่อสนาม>");
                    return true;
                }
                String name = a[1].toLowerCase(Locale.ROOT);
                if (getConfig().getConfigurationSection("arenas." + name) == null) {
                    Msg.send(s, "&cไม่พบสนาม " + name);
                    return true;
                }
                getConfig().set("arenas." + name, null);
                saveConfig();
                Msg.send(s, "&aลบสนาม &e" + name + " &aแล้ว");
            }
            case "arenas" -> {
                if (!s.hasPermission("kitduel.admin")) {
                    Msg.send(s, "&cไม่มีสิทธิ์");
                    return true;
                }
                ConfigurationSection sec = getConfig().getConfigurationSection("arenas");
                if (sec == null || sec.getKeys(false).isEmpty()) {
                    Msg.send(s, "&7ยังไม่มีสนามที่ตั้งไว้ (ใช้จุดเกิดของโลก pvp เป็นสนามเริ่มต้น)");
                } else {
                    Msg.send(s, "&7สนาม: &f" + String.join("&7, &f", sec.getKeys(false)));
                }
            }
            case "reload" -> {
                if (!s.hasPermission("kitduel.admin")) {
                    Msg.send(s, "&cไม่มีสิทธิ์");
                    return true;
                }
                reloadConfig();
                Msg.send(s, "&aรีโหลด config แล้ว");
            }
            default -> help(s);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "sword", "leave"));
            if (s.hasPermission("kitduel.admin")) subs.addAll(List.of("setspawn", "delarena", "arenas", "reload"));
            for (String x : subs) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("sword") && s.hasPermission("kitduel.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("setspawn")) {
            out.add("1");
            out.add("2");
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("delarena") || a[0].equalsIgnoreCase("setspawn"))) {
            ConfigurationSection sec = getConfig().getConfigurationSection("arenas");
            if (sec != null) out.addAll(sec.getKeys(false));
        }
        return out;
    }
}
