package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /party — สร้างปาตี้เพื่อดวลทีม (มากกว่า 2 คน) */
public final class PartyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of(
            "create", "invite", "accept", "deny", "leave", "kick", "promote", "disband", "list", "sword",
            "acceptduel", "help");

    private final KitDuelPlugin plugin;
    private final DuelManager duels;
    private final PartyManager parties;

    public PartyCommand(KitDuelPlugin plugin, DuelManager duels) {
        this.plugin = plugin;
        this.duels = duels;
        this.parties = duels.parties();
    }

    private void help(CommandSender s) {
        s.sendMessage(Msg.LINE);
        s.sendMessage(Msg.c("&d&l  Party Duel &7— ดวลทีมมากกว่า 2 คน"));
        s.sendMessage(Msg.c("&e  /party create &7— สร้างปาตี้ + รับ &dดาบปาตี้ดวล"));
        s.sendMessage(Msg.c("&e  /party invite <ชื่อ> &7— เชิญเข้าปาตี้ (หรือคลิกขวาด้วยดาบปาตี้)"));
        s.sendMessage(Msg.c("&e  /party accept &7| &e/party deny &7— รับ/ปฏิเสธคำเชิญ"));
        s.sendMessage(Msg.c("&e  /party list &7| &e/party leave"));
        s.sendMessage(Msg.c("&e  /party kick <ชื่อ> &7| &e/party promote <ชื่อ> &7| &e/party disband"));
        s.sendMessage(Msg.c("&e  /party acceptduel &7— หัวหน้ารับคำท้าดวลปาตี้"));
        s.sendMessage(Msg.c("&e  /party sword &7— (หัวหน้า) รับดาบปาตี้อีกครั้ง"));
        s.sendMessage(Msg.c("&7  ▸ หัวหน้าถือดาบปาตี้ &fคลิกขวาที่ผู้เล่นปาตี้อื่น &7→ เลือก Kit → ท้าดวลปาตี้"));
        s.sendMessage(Msg.LINE);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Msg.send(s, "&cใช้ได้เฉพาะผู้เล่น");
            return true;
        }
        if (!p.hasPermission("kitduel.use")) {
            Msg.send(p, "&cคุณไม่มีสิทธิ์ใช้ระบบดวล");
            return true;
        }
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(p);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        // ระหว่างดวลดูรายชื่อได้อย่างเดียว (ห้ามเปลี่ยนปาตี้ — ยอมแพ้ใช้ /duel leave)
        if (duels.duelOf(p) != null && !sub.equals("list")) {
            Msg.send(p, "&cใช้คำสั่งนี้ระหว่างดวลไม่ได้ (ยอมแพ้: &f/duel leave&c)");
            return true;
        }
        switch (sub) {
            case "create" -> parties.create(p);
            case "invite" -> {
                if (a.length < 2) {
                    Msg.send(p, "&c/party invite <ผู้เล่น>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    Msg.send(p, "&cไม่พบผู้เล่น " + a[1]);
                    return true;
                }
                parties.invite(p, t);
            }
            case "accept" -> parties.accept(p);
            case "deny" -> parties.deny(p);
            case "leave" -> parties.leave(p);
            case "kick" -> {
                if (a.length < 2) {
                    Msg.send(p, "&c/party kick <ผู้เล่น>");
                    return true;
                }
                parties.kick(p, a[1]);
            }
            case "promote" -> {
                if (a.length < 2) {
                    Msg.send(p, "&c/party promote <ผู้เล่น>");
                    return true;
                }
                parties.promote(p, a[1]);
            }
            case "disband" -> parties.disband(p);
            case "list" -> parties.list(p);
            case "acceptduel" -> duels.acceptPartyDuel(p);
            case "sword" -> {
                PartyManager.Party party = parties.of(p);
                if (party == null || !party.isLeader(p.getUniqueId())) {
                    Msg.send(p, "&cเฉพาะหัวหน้าปาตี้เท่านั้นที่มีดาบปาตี้");
                } else if (Items.hasPartySword(p)) {
                    Msg.send(p, "&eคุณมีดาบปาตี้อยู่แล้ว");
                } else {
                    Items.givePartySword(plugin, p);
                    Msg.send(p, "&aได้รับ &d&lดาบปาตี้ดวล &aแล้ว!");
                }
            }
            default -> help(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : SUBS) if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && s instanceof Player p) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            String pre = a[1].toLowerCase(Locale.ROOT);
            if (sub.equals("invite")) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.equals(p) && o.getName().toLowerCase(Locale.ROOT).startsWith(pre)) out.add(o.getName());
                }
            } else if (sub.equals("kick") || sub.equals("promote")) {
                PartyManager.Party party = parties.of(p);
                if (party != null) {
                    for (java.util.UUID id : party.members) {
                        String n = party.names.get(id);
                        if (n != null && !id.equals(p.getUniqueId()) && n.toLowerCase(Locale.ROOT).startsWith(pre)) out.add(n);
                    }
                }
            }
        }
        return out;
    }
}
