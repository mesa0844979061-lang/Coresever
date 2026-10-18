package com.kitduel;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Msg {
    private Msg() {}

    public static final String PREFIX = c("&8[&b&lKit&3&lDuel&8] &7");
    public static final String LINE = c("&8&m                                          ");

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender s, String m) {
        s.sendMessage(PREFIX + c(m));
    }

    public static void box(Player p, String... lines) {
        p.sendMessage(LINE);
        for (String l : lines) p.sendMessage(c(l));
        p.sendMessage(LINE);
    }

    public static void title(Player p, String t, String sub, int in, int stay, int out) {
        p.sendTitle(c(t), c(sub), in, stay, out);
    }

    public static void sound(Player p, Sound s, float pitch) {
        p.playSound(p.getLocation(), s, 1f, pitch);
    }
}
