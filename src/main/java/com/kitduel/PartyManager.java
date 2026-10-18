package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** ระบบปาตี้: สร้าง/เชิญ/รับ/ออก/เตะ/ยุบ — หัวหน้าปาตี้ได้ "ดาบปาตี้ดวล" */
public final class PartyManager {

    public static final class Party {
        public final UUID id = UUID.randomUUID();
        public UUID leader;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        public final Map<UUID, String> names = new HashMap<>();

        public boolean isLeader(UUID u) {
            return leader != null && leader.equals(u);
        }

        public String leaderName() {
            return names.getOrDefault(leader, "?");
        }

        public String displayName() {
            return "ปาตี้ของ " + leaderName();
        }

        public int size() {
            return members.size();
        }

        public List<Player> online() {
            List<Player> out = new ArrayList<>();
            for (UUID id : members) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) out.add(p);
            }
            return out;
        }
    }

    private record Invite(UUID partyId, String fromName, long expires) {}

    private final KitDuelPlugin plugin;
    private final DuelManager duels;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Map<UUID, Party> byId = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public PartyManager(KitDuelPlugin plugin, DuelManager duels) {
        this.plugin = plugin;
        this.duels = duels;
    }

    // ------------------------------------------------------------------ queries

    public Party of(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public Party of(UUID id) {
        return byPlayer.get(id);
    }

    public Party byId(UUID partyId) {
        return byId.get(partyId);
    }

    public int maxSize() {
        return Math.max(2, plugin.getConfig().getInt("party.max-size", 8));
    }

    private long inviteMillis() {
        return Math.max(10, plugin.getConfig().getInt("party.invite-expire-seconds", 60)) * 1000L;
    }

    private boolean inDuel(UUID id) {
        return duels.duelOf(id) != null;
    }

    private void broadcast(Party party, String msg) {
        for (Player p : party.online()) Msg.send(p, msg);
    }

    /** ให้ดาบปาตี้เฉพาะหัวหน้า / เก็บคืนจากคนที่ไม่ใช่หัวหน้า (ข้ามคนที่กำลังดวลอยู่ — ซิงค์ใหม่ตอนจบดวล) */
    public void syncSword(Player p) {
        if (p == null || !p.isOnline() || duels.isBusy(p)) return;
        Party party = of(p);
        boolean should = party != null && party.isLeader(p.getUniqueId());
        boolean has = Items.hasPartySword(p);
        if (should && !has) {
            Items.givePartySword(plugin, p);
        } else if (!should && has) {
            Items.removePartySwords(p);
        }
    }

    // ------------------------------------------------------------------ actions

    public void create(Player p) {
        if (of(p) != null) {
            Msg.send(p, "&cคุณอยู่ในปาตี้แล้ว (&f/party leave &cเพื่อออก)");
            return;
        }
        if (duels.isBusy(p)) {
            Msg.send(p, "&cสร้างปาตี้ระหว่างดวลไม่ได้");
            return;
        }
        Party party = new Party();
        party.leader = p.getUniqueId();
        party.members.add(p.getUniqueId());
        party.names.put(p.getUniqueId(), p.getName());
        byPlayer.put(p.getUniqueId(), party);
        byId.put(party.id, party);
        invites.remove(p.getUniqueId());
        syncSword(p);
        Msg.box(p,
                "&d&l  สร้างปาตี้สำเร็จ!",
                "&7  ได้รับ &d&lดาบปาตี้ดวล &7แล้ว",
                "&7  ▸ คลิกขวาผู้เล่น (ถือดาบปาตี้) เพื่อ &fเชิญเข้าปาตี้",
                "&7  ▸ คลิกขวาผู้เล่นปาตี้อื่น เพื่อ &fท้าดวลปาตี้",
                "&7  ▸ หรือใช้ &f/party invite <ชื่อ>");
        Msg.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.3f);
    }

    public void invite(Player leader, Player target) {
        Party party = of(leader);
        if (party == null) {
            Msg.send(leader, "&cคุณยังไม่มีปาตี้ — &f/party create");
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            Msg.send(leader, "&cเฉพาะหัวหน้าปาตี้เท่านั้นที่เชิญคนได้");
            return;
        }
        if (leader.equals(target)) return;
        if (target.hasMetadata("NPC")) return;
        if (of(target) != null) {
            Msg.send(leader, "&e" + target.getName() + " &cอยู่ในปาตี้แล้ว");
            return;
        }
        if (duels.isBusy(target)) {
            Msg.send(leader, "&e" + target.getName() + " &cกำลังดวลอยู่");
            return;
        }
        if (party.size() >= maxSize()) {
            Msg.send(leader, "&cปาตี้เต็มแล้ว (สูงสุด &f" + maxSize() + " &cคน)");
            return;
        }
        Invite old = invites.get(target.getUniqueId());
        if (old != null && old.partyId().equals(party.id) && System.currentTimeMillis() <= old.expires()) {
            Msg.send(leader, "&7เชิญ &f" + target.getName() + " &7ไปแล้ว รอให้เขารับ");
            return;
        }
        invites.put(target.getUniqueId(), new Invite(party.id, leader.getName(), System.currentTimeMillis() + inviteMillis()));
        Msg.send(leader, "&aส่งคำเชิญไปที่ &f" + target.getName() + " &aแล้ว");
        Msg.box(target,
                "&d&l  ⚔ คำเชิญเข้าปาตี้!",
                "&7  จาก: &f" + leader.getName() + " &7(" + party.size() + " คน)",
                "&a  พิมพ์ &f/party accept &aเพื่อเข้าร่วม &7| &c/party deny &7เพื่อปฏิเสธ",
                "&7  หมดเวลาใน &e" + (inviteMillis() / 1000) + " &7วินาที");
        Msg.sound(target, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
    }

    public void accept(Player p) {
        Invite inv = invites.get(p.getUniqueId());
        if (inv == null || System.currentTimeMillis() > inv.expires()) {
            invites.remove(p.getUniqueId());
            Msg.send(p, "&cไม่มีคำเชิญเข้าปาตี้ (หรือหมดเวลาแล้ว)");
            return;
        }
        if (of(p) != null) {
            Msg.send(p, "&cคุณอยู่ในปาตี้แล้ว");
            return;
        }
        if (duels.isBusy(p)) {
            Msg.send(p, "&cเข้าปาตี้ระหว่างดวลไม่ได้");
            return;
        }
        Party party = byId.get(inv.partyId());
        invites.remove(p.getUniqueId());
        if (party == null) {
            Msg.send(p, "&cปาตี้นี้ถูกยุบไปแล้ว");
            return;
        }
        if (party.size() >= maxSize()) {
            Msg.send(p, "&cปาตี้เต็มแล้ว");
            return;
        }
        party.members.add(p.getUniqueId());
        party.names.put(p.getUniqueId(), p.getName());
        byPlayer.put(p.getUniqueId(), party);
        broadcast(party, "&a" + p.getName() + " &7เข้าร่วมปาตี้ &8(" + party.size() + "/" + maxSize() + ")");
        Msg.sound(p, Sound.ENTITY_PLAYER_LEVELUP, 1.3f);
    }

    public void deny(Player p) {
        Invite inv = invites.remove(p.getUniqueId());
        if (inv == null) {
            Msg.send(p, "&cไม่มีคำเชิญให้ปฏิเสธ");
            return;
        }
        Msg.send(p, "&7ปฏิเสธคำเชิญแล้ว");
        Player from = Bukkit.getPlayerExact(inv.fromName());
        if (from != null) Msg.send(from, "&e" + p.getName() + " &cปฏิเสธคำเชิญเข้าปาตี้");
    }

    public void leave(Player p) {
        Party party = of(p);
        if (party == null) {
            Msg.send(p, "&cคุณไม่ได้อยู่ในปาตี้");
            return;
        }
        removeMember(party, p.getUniqueId(), p.getName() + " &7ออกจากปาตี้");
        Msg.send(p, "&7คุณออกจากปาตี้แล้ว");
        syncSword(p);
    }

    public void kick(Player leader, String name) {
        Party party = of(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            Msg.send(leader, "&cเฉพาะหัวหน้าปาตี้เท่านั้น");
            return;
        }
        UUID target = findMember(party, name);
        if (target == null) {
            Msg.send(leader, "&cไม่พบ &f" + name + " &cในปาตี้");
            return;
        }
        if (target.equals(leader.getUniqueId())) {
            Msg.send(leader, "&cเตะตัวเองไม่ได้ (ใช้ &f/party leave &cหรือ &f/party disband&c)");
            return;
        }
        if (inDuel(target)) {
            Msg.send(leader, "&cเขากำลังดวลอยู่ เตะไม่ได้ตอนนี้");
            return;
        }
        String n = party.names.getOrDefault(target, name);
        Player tp = Bukkit.getPlayer(target);
        removeMember(party, target, "&c" + n + " &7ถูกเตะออกจากปาตี้");
        if (tp != null) {
            Msg.send(tp, "&cคุณถูกเตะออกจากปาตี้");
            syncSword(tp);
        }
    }

    public void promote(Player leader, String name) {
        Party party = of(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            Msg.send(leader, "&cเฉพาะหัวหน้าปาตี้เท่านั้น");
            return;
        }
        UUID target = findMember(party, name);
        if (target == null || target.equals(leader.getUniqueId())) {
            Msg.send(leader, "&cระบุสมาชิกคนอื่นในปาตี้");
            return;
        }
        if (inDuel(target)) {
            Msg.send(leader, "&cเขากำลังดวลอยู่ โอนหัวหน้าไม่ได้ตอนนี้");
            return;
        }
        party.leader = target;
        broadcast(party, "&d" + party.names.getOrDefault(target, name) + " &7เป็นหัวหน้าปาตี้คนใหม่");
        for (Player m : party.online()) syncSword(m);
        duels.dropPartyRequests(party.id);
    }

    public void disband(Player leader) {
        Party party = of(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            Msg.send(leader, "&cเฉพาะหัวหน้าปาตี้เท่านั้น");
            return;
        }
        for (UUID id : party.members) {
            if (!id.equals(leader.getUniqueId()) && inDuel(id)) {
                Msg.send(leader, "&cมีสมาชิกกำลังดวลอยู่ ยุบปาตี้ไม่ได้ตอนนี้");
                return;
            }
        }
        List<Player> online = party.online();
        destroy(party);
        for (Player m : online) {
            Msg.send(m, "&cปาตี้ถูกยุบแล้ว");
            syncSword(m);
        }
    }

    public void list(Player p) {
        Party party = of(p);
        if (party == null) {
            Msg.send(p, "&cคุณไม่ได้อยู่ในปาตี้ — &f/party create");
            return;
        }
        p.sendMessage(Msg.LINE);
        p.sendMessage(Msg.c("&d&l  ปาตี้ &7(" + party.size() + "/" + maxSize() + ")"));
        for (UUID id : party.members) {
            boolean lead = party.isLeader(id);
            Player m = Bukkit.getPlayer(id);
            String n = party.names.getOrDefault(id, "?");
            String status = m == null ? " &8(ออฟไลน์)" : inDuel(id) ? " &c(กำลังดวล)" : "";
            p.sendMessage(Msg.c("&7  ▸ " + (lead ? "&6★ &f" : "&f") + n + status));
        }
        p.sendMessage(Msg.LINE);
    }

    // ------------------------------------------------------------------ lifecycle hooks

    /** ผู้เล่นออกจากเซิร์ฟ: ออกจากปาตี้อัตโนมัติ (เรียกหลัง DuelManager.handleQuit) */
    public void onQuit(Player p) {
        invites.remove(p.getUniqueId());
        invites.values().removeIf(i -> i.fromName().equalsIgnoreCase(p.getName()));
        Party party = of(p);
        if (party != null) removeMember(party, p.getUniqueId(), "&e" + p.getName() + " &7ออกจากเซิร์ฟ — ออกจากปาตี้");
    }

    // ------------------------------------------------------------------ internals

    private UUID findMember(Party party, String name) {
        for (UUID id : party.members) {
            String n = party.names.get(id);
            if (n != null && n.equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    private void destroy(Party party) {
        for (UUID id : party.members) byPlayer.remove(id);
        byId.remove(party.id);
        invites.values().removeIf(i -> i.partyId().equals(party.id));
        duels.dropPartyRequests(party.id);
    }

    private void removeMember(Party party, UUID id, String notice) {
        party.members.remove(id);
        byPlayer.remove(id);
        if (party.members.isEmpty()) {
            destroy(party);
            return;
        }
        if (party.isLeader(id)) {
            // โอนหัวหน้าให้คนที่ไม่ได้อยู่ในดวล (ถ้าไม่มีก็คนแรกในรายการ)
            UUID next = null;
            for (UUID m : party.members) {
                if (!inDuel(m)) {
                    next = m;
                    break;
                }
            }
            if (next == null) next = party.members.iterator().next();
            party.leader = next;
            duels.dropPartyRequests(party.id);
            broadcast(party, notice);
            broadcast(party, "&d" + party.names.getOrDefault(next, "?") + " &7เป็นหัวหน้าปาตี้คนใหม่");
            for (Player m : party.online()) syncSword(m);
        } else {
            broadcast(party, notice);
        }
    }

    public List<String> memberNames(Party party) {
        List<String> out = new ArrayList<>();
        for (UUID id : party.members) out.add(party.names.getOrDefault(id, "?").toLowerCase(Locale.ROOT));
        return out;
    }
}
