package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class DuelManager {
    private static final int MAX_TRACKED_BLOCKS = 6000;

    /**
     * คำท้า: คีย์ของ map = ผู้ท้า (UUID ผู้เล่น ถ้า 1v1 / UUID ปาตี้ ถ้าปาตี้ดวล)
     * target = ผู้ถูกท้า (UUID ผู้เล่น / UUID ปาตี้)
     */
    private record Request(UUID target, Kit kit, long expires, boolean party) {}

    private final KitDuelPlugin plugin;
    private final KitRegistry kits;
    private final PartyManager parties;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Duel> byPlayer = new HashMap<>();
    private final Set<Duel> duels = new HashSet<>();
    private final Set<String> usedArenas = new HashSet<>();
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();
    private final Map<UUID, Long> clickCd = new HashMap<>();
    private final File stateDir;
    private BukkitTask expiryTask;

    public DuelManager(KitDuelPlugin plugin, KitRegistry kits) {
        this.plugin = plugin;
        this.kits = kits;
        this.parties = new PartyManager(plugin, this);
        this.stateDir = new File(plugin.getDataFolder(), "states");
        if (!stateDir.exists() && !stateDir.mkdirs()) {
            plugin.getLogger().warning("สร้างโฟลเดอร์ states ไม่สำเร็จ");
        }
    }

    public void start() {
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRequests, 20L, 20L);
    }

    public KitRegistry kits() {
        return kits;
    }

    public PartyManager parties() {
        return parties;
    }

    // ------------------------------------------------------------------ queries

    public Duel duelOf(Player p) {
        return byPlayer.get(p.getUniqueId());
    }

    public Duel duelOf(UUID id) {
        return byPlayer.get(id);
    }

    public boolean isBusy(Player p) {
        return byPlayer.containsKey(p.getUniqueId()) || states.containsKey(p.getUniqueId());
    }

    public boolean isBypass(Player p) {
        return bypass.contains(p.getUniqueId());
    }

    public String pvpWorldName() {
        return plugin.getConfig().getString("pvp-world", "pvp");
    }

    public Duel duelTracking(Block b) {
        for (Duel d : duels) if (d.changed.containsKey(b)) return d;
        return null;
    }

    /** บันทึกบล็อกเดิมไว้ก่อนถูกเปลี่ยน (เพื่อคืนสนามหลังจบดวล) คืน false ถ้าเกินลิมิต */
    public boolean track(Duel d, Block b, BlockState original) {
        if (d.changed.containsKey(b)) return true;
        if (d.changed.size() >= MAX_TRACKED_BLOCKS) return false;
        d.changed.put(b, original);
        return true;
    }

    // ------------------------------------------------------------------ challenge flow (1v1)

    private boolean onCooldown(Player p) {
        long now = System.currentTimeMillis();
        Long last = clickCd.get(p.getUniqueId());
        if (last != null && now - last < 400) return true;
        clickCd.put(p.getUniqueId(), now);
        return false;
    }

    private boolean cannotBeChallenged(Player b) {
        return b.getGameMode() == GameMode.SPECTATOR || b.isDead();
    }

    /** a คลิกขวาที่ b ด้วยดาบท้าดวล */
    public void handleSwordClick(Player a, Player b) {
        if (onCooldown(a)) return;
        if (!a.hasPermission("kitduel.use")) {
            Msg.send(a, "&cคุณไม่มีสิทธิ์ใช้ระบบดวล");
            return;
        }
        if (a.equals(b)) return;
        if (isBusy(a)) {
            Msg.send(a, "&cคุณกำลังอยู่ในดวลอยู่");
            return;
        }
        if (isBusy(b)) {
            Msg.send(a, "&e" + b.getName() + " &cกำลังดวลอยู่ ลองใหม่ภายหลัง");
            return;
        }
        if (cannotBeChallenged(b)) {
            Msg.send(a, "&cไม่สามารถท้าผู้เล่นคนนี้ได้ตอนนี้");
            return;
        }
        // a กำลังรับคำท้าของ b ?
        Request r = requests.get(b.getUniqueId());
        if (r != null && !r.party() && r.target().equals(a.getUniqueId())) {
            if (System.currentTimeMillis() <= r.expires()) {
                requests.remove(b.getUniqueId());
                startDuel(b, a, r.kit());
                return;
            }
            requests.remove(b.getUniqueId());
        }
        KitGui.open(a, b, kits);
    }

    /** a เลือก kit จาก GUI -> ส่งคำท้าไปหา target */
    public void sendChallenge(Player a, UUID targetId, Kit kit) {
        Player b = Bukkit.getPlayer(targetId);
        if (b == null || !b.isOnline()) {
            Msg.send(a, "&cผู้เล่นที่คุณท้าออฟไลน์แล้ว");
            return;
        }
        if (isBusy(a) || isBusy(b)) {
            Msg.send(a, "&cคุณหรืออีกฝ่ายกำลังอยู่ในดวล");
            return;
        }
        if (cannotBeChallenged(b)) {
            Msg.send(a, "&cไม่สามารถท้าผู้เล่นคนนี้ได้ตอนนี้");
            return;
        }
        Request rev = requests.get(b.getUniqueId());
        if (rev != null && !rev.party() && rev.target().equals(a.getUniqueId())
                && System.currentTimeMillis() <= rev.expires()) {
            Msg.send(a, "&e" + b.getName() + " &7ท้าคุณอยู่แล้ว! ถือดาบแล้ว &fคลิกขวาที่เขา &7เพื่อรับคำท้า");
            return;
        }
        int secs = Math.max(5, plugin.getConfig().getInt("request-expire-seconds", 30));
        requests.put(a.getUniqueId(), new Request(b.getUniqueId(), kit, System.currentTimeMillis() + secs * 1000L, false));

        Msg.box(a,
                "&b&l  ส่งคำท้าดวลแล้ว!",
                "&7  คู่ต่อสู้: &f" + b.getName(),
                "&7  KIT: " + kit.displayName(),
                "&7  รอให้เขารับภายใน &e" + secs + " &7วินาที");
        Msg.sound(a, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);

        Msg.box(b,
                "&c&l  ⚔ มีคนท้าดวลคุณ!",
                "&7  จาก: &f" + a.getName(),
                "&7  KIT: " + kit.displayName(),
                "&7  &aถือดาบท้าดวล แล้วคลิกขวาที่ &f" + a.getName() + " &aเพื่อรับ",
                "&7  หมดเวลาใน &e" + secs + " &7วินาที");
        Msg.title(b, "&c&l⚔ คำท้าดวล ⚔", "&f" + a.getName() + " &7| " + kit.displayName(), 5, 50, 10);
        Msg.sound(b, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f);
    }

    private void tickRequests() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Request>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Request> en = it.next();
            if (now > en.getValue().expires()) {
                it.remove();
                if (en.getValue().party()) {
                    PartyManager.Party p = parties.byId(en.getKey());
                    if (p != null) {
                        for (Player m : p.online()) Msg.send(m, "&cคำท้าดวลปาตี้ของคุณหมดเวลาแล้ว");
                    }
                } else {
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p != null) Msg.send(p, "&cคำท้าดวลของคุณหมดเวลาแล้ว");
                }
            }
        }
    }

    // ------------------------------------------------------------------ challenge flow (party)

    /** หัวหน้าปาตี้ a คลิกขวาที่ b ด้วยดาบปาตี้ดวล */
    public void handlePartySwordClick(Player a, Player b) {
        if (onCooldown(a)) return;
        if (!a.hasPermission("kitduel.use")) {
            Msg.send(a, "&cคุณไม่มีสิทธิ์ใช้ระบบดวล");
            return;
        }
        if (a.equals(b)) return;
        PartyManager.Party pa = parties.of(a);
        if (pa == null) {
            Msg.send(a, "&cคุณยังไม่มีปาตี้ — &f/party create");
            return;
        }
        if (!pa.isLeader(a.getUniqueId())) {
            Msg.send(a, "&cเฉพาะหัวหน้าปาตี้เท่านั้นที่ใช้ดาบนี้ได้");
            return;
        }
        if (isBusy(a)) {
            Msg.send(a, "&cคุณกำลังอยู่ในดวลอยู่");
            return;
        }
        PartyManager.Party pb = parties.of(b);
        if (pb == null) {
            parties.invite(a, b); // ผู้เล่นไม่มีปาตี้ = เชิญเข้าปาตี้
            return;
        }
        if (pb == pa) {
            parties.list(a);
            return;
        }
        // หัวหน้า a กำลังรับคำท้าของปาตี้ pb ?
        Request r = requests.get(pb.id);
        if (r != null && r.party() && r.target().equals(pa.id)) {
            requests.remove(pb.id);
            if (System.currentTimeMillis() <= r.expires()) {
                startPartyDuel(pb, pa, r.kit());
                return;
            }
        }
        Player head = Bukkit.getPlayer(pb.leader);
        KitGui.openParty(a, pb.id, pb.displayName(), pb.size(), head != null ? head : b, kits);
    }

    /** /party acceptduel — หัวหน้ารับคำท้าปาตี้ที่ค้างอยู่ */
    public void acceptPartyDuel(Player leader) {
        PartyManager.Party mine = parties.of(leader);
        if (mine == null || !mine.isLeader(leader.getUniqueId())) {
            Msg.send(leader, "&cเฉพาะหัวหน้าปาตี้เท่านั้น");
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Request> en : new ArrayList<>(requests.entrySet())) {
            Request r = en.getValue();
            if (!r.party() || !r.target().equals(mine.id)) continue;
            requests.remove(en.getKey());
            if (now > r.expires()) continue;
            PartyManager.Party challenger = parties.byId(en.getKey());
            if (challenger == null) continue;
            startPartyDuel(challenger, mine, r.kit());
            return;
        }
        Msg.send(leader, "&cไม่มีคำท้าดวลปาตี้ที่รออยู่");
    }

    public void sendPartyChallenge(Player a, UUID targetPartyId, Kit kit) {
        PartyManager.Party pa = parties.of(a);
        PartyManager.Party pb = parties.byId(targetPartyId);
        if (pa == null || !pa.isLeader(a.getUniqueId())) {
            Msg.send(a, "&cเฉพาะหัวหน้าปาตี้เท่านั้นที่ท้าได้");
            return;
        }
        if (pb == null) {
            Msg.send(a, "&cปาตี้ที่คุณท้าถูกยุบไปแล้ว");
            return;
        }
        if (pa == pb) return;
        if (!checkPartyReady(a, pa) || !checkPartyReady(a, pb)) return;
        if (plugin.getConfig().getBoolean("party.require-equal-size", false) && pa.size() != pb.size()) {
            Msg.send(a, "&cจำนวนสมาชิกต้องเท่ากัน (&f" + pa.size() + " &cvs &f" + pb.size() + "&c)");
            return;
        }
        Request rev = requests.get(pb.id);
        if (rev != null && rev.party() && rev.target().equals(pa.id) && System.currentTimeMillis() <= rev.expires()) {
            Msg.send(a, "&e" + pb.displayName() + " &7ท้าปาตี้คุณอยู่แล้ว! คลิกขวาที่เขาด้วยดาบปาตี้ หรือ &f/party acceptduel");
            return;
        }
        int secs = Math.max(5, plugin.getConfig().getInt("request-expire-seconds", 30));
        requests.put(pa.id, new Request(pb.id, kit, System.currentTimeMillis() + secs * 1000L, true));

        for (Player m : pa.online()) {
            Msg.box(m,
                    "&d&l  ส่งคำท้าดวลปาตี้แล้ว!",
                    "&7  คู่ต่อสู้: &f" + pb.displayName() + " &7(" + pb.size() + " คน)",
                    "&7  KIT: " + kit.displayName(),
                    "&7  รอให้หัวหน้าเขารับภายใน &e" + secs + " &7วินาที");
        }
        for (Player m : pb.online()) {
            boolean lead = pb.isLeader(m.getUniqueId());
            Msg.box(m,
                    "&c&l  ⚔ มีปาตี้ท้าดวล!",
                    "&7  จาก: &f" + pa.displayName() + " &7(" + pa.size() + " คน)",
                    "&7  KIT: " + kit.displayName(),
                    lead ? "&7  &aถือดาบปาตี้ คลิกขวาที่ &f" + pa.leaderName() + " &aหรือพิมพ์ &f/party acceptduel"
                            : "&7  รอหัวหน้าปาตี้ของคุณกดรับ",
                    "&7  หมดเวลาใน &e" + secs + " &7วินาที");
            if (lead) {
                Msg.title(m, "&c&l⚔ ปาตี้ท้าดวล ⚔", "&f" + pa.displayName() + " &7| " + kit.displayName(), 5, 50, 10);
                Msg.sound(m, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f);
            }
        }
    }

    /** ทุกคนในปาตี้ต้องออนไลน์ ไม่ติดดวล และไม่ตาย/สเปกเตเตอร์ */
    private boolean checkPartyReady(Player notify, PartyManager.Party party) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m == null || !m.isOnline()) {
                Msg.send(notify, "&e" + party.names.getOrDefault(id, "?") + " &cออฟไลน์อยู่");
                return false;
            }
            if (isBusy(m)) {
                Msg.send(notify, "&e" + m.getName() + " &cกำลังดวลอยู่");
                return false;
            }
            if (cannotBeChallenged(m)) {
                Msg.send(notify, "&e" + m.getName() + " &cยังไม่พร้อม (ตาย/สเปกเตเตอร์)");
                return false;
            }
        }
        return true;
    }

    private void startPartyDuel(PartyManager.Party challenger, PartyManager.Party accepter, Kit kit) {
        List<Player> ta = challenger.online();
        List<Player> tb = accepter.online();
        if (ta.size() != challenger.size() || tb.size() != accepter.size()) {
            for (Player p : ta) Msg.send(p, "&cมีสมาชิกออฟไลน์ ยกเลิกการดวลปาตี้");
            for (Player p : tb) Msg.send(p, "&cมีสมาชิกออฟไลน์ ยกเลิกการดวลปาตี้");
            return;
        }
        Player notify = Bukkit.getPlayer(accepter.leader);
        if (notify == null || !checkPartyReady(notify, challenger) || !checkPartyReady(notify, accepter)) {
            for (Player p : ta) Msg.send(p, "&cเริ่มดวลปาตี้ไม่ได้ (มีสมาชิกไม่พร้อม)");
            return;
        }
        requests.remove(challenger.id);
        requests.remove(accepter.id);
        launch(ta, challenger.displayName(), tb, accepter.displayName(), kit, true);
    }

    /** ลบคำท้าที่เกี่ยวกับปาตี้นี้ (ตอนยุบ/เปลี่ยนหัวหน้า) */
    public void dropPartyRequests(UUID partyId) {
        requests.remove(partyId);
        requests.values().removeIf(r -> r.party() && r.target().equals(partyId));
    }

    // ------------------------------------------------------------------ arena / world

    private Location parseLoc(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length < 6) return null;
        World w = Bukkit.getWorld(p[0].trim());
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()),
                    Double.parseDouble(p[3].trim()), Float.parseFloat(p[4].trim()), Float.parseFloat(p[5].trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static String formatLoc(Location l) {
        return String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.1f,%.1f",
                l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    private World ensureWorld() {
        String name = pvpWorldName();
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        if (Bukkit.getPluginManager().isPluginEnabled("Multiverse-Core")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + name);
            w = Bukkit.getWorld(name);
        }
        return w;
    }

    private Arena pickArena(World pvp) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("arenas");
        int valid = 0;
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                Location s1 = parseLoc(sec.getString(name + ".spawn1"));
                Location s2 = parseLoc(sec.getString(name + ".spawn2"));
                if (s1 == null || s2 == null) continue;
                valid++;
                if (!usedArenas.contains(name)) return new Arena(name, s1, s2);
            }
        }
        if (valid > 0) return null; // มีสนามที่ตั้งไว้ แต่เต็มหมด
        if (usedArenas.contains("default")) return null;
        Location sp = pvp.getSpawnLocation();
        int bx = sp.getBlockX(), bz = sp.getBlockZ();
        Location s1 = new Location(pvp, bx - 8 + 0.5, pvp.getHighestBlockYAt(bx - 8, bz) + 1, bz + 0.5, -90f, 0f);
        Location s2 = new Location(pvp, bx + 8 + 0.5, pvp.getHighestBlockYAt(bx + 8, bz) + 1, bz + 0.5, 90f, 0f);
        return new Arena("default", s1, s2);
    }

    /** กระจายตำแหน่งเกิดของแต่ละทีมเป็นแนวขวางกับเส้นระหว่างสองฝั่ง (ถ้าตำแหน่งไม่ปลอดภัยใช้จุดเกิดเดิม) */
    private void assignSpawns(Duel d) {
        Location s1 = d.arena.spawn1(), s2 = d.arena.spawn2();
        double dx = s2.getX() - s1.getX(), dz = s2.getZ() - s1.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        double px, pz; // เวกเตอร์ขวางเส้น
        if (len < 0.001) {
            px = 1;
            pz = 0;
        } else {
            px = -dz / len;
            pz = dx / len;
        }
        for (int t = 0; t < 2; t++) {
            List<UUID> team = d.team(t);
            Location base = t == 0 ? s1 : s2;
            int n = team.size();
            for (int i = 0; i < n; i++) {
                double off = (i - (n - 1) / 2.0) * 1.6;
                Location l = base.clone().add(px * off, 0, pz * off);
                d.spawns.put(team.get(i), safeSpot(l) ? l : base.clone());
            }
        }
    }

    private boolean safeSpot(Location l) {
        try {
            Block feet = l.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            Block below = feet.getRelative(0, -1, 0);
            return feet.isPassable() && head.isPassable() && !below.isPassable() && !below.isLiquid();
        } catch (Exception ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ state save / restore

    private File stateFile(UUID id) {
        return new File(stateDir, id + ".yml");
    }

    private void saveState(Player p) throws IOException {
        PlayerState st = PlayerState.capture(p);
        states.put(p.getUniqueId(), st);
        st.save(stateFile(p.getUniqueId()));
    }

    private boolean teleportBypass(Player p, Location loc) {
        bypass.add(p.getUniqueId());
        try {
            return p.teleport(loc);
        } finally {
            bypass.remove(p.getUniqueId());
        }
    }

    private void applyState(Player p) {
        UUID id = p.getUniqueId();
        PlayerState st = states.get(id);
        if (st == null || !p.isOnline() || p.isDead() || byPlayer.containsKey(id)) return;
        states.remove(id);
        try {
            st.restore(p);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "คืนค่าผู้เล่น " + p.getName() + " ไม่สำเร็จ", ex);
        }
        File f = stateFile(id);
        if (f.exists() && !f.delete()) plugin.getLogger().warning("ลบไฟล์สถานะไม่ได้: " + f.getName());
        Msg.send(p, "&aคืนไอเทมและตำแหน่งเดิมของคุณเรียบร้อยแล้ว");
        // ดาบปาตี้อาจเปลี่ยนมือระหว่างดวล (หัวหน้าออก/โอน) — ซิงค์ให้ตรงกับสถานะปัจจุบัน
        parties.syncSword(p);
    }

    /** วาปกลับ + คืนของ (immediate=false จะหน่วง 2 tick ให้ปลั๊กอินโลกอื่นทำงานก่อน) */
    private void returnPlayer(Player p, boolean immediate) {
        PlayerState st = states.get(p.getUniqueId());
        if (st == null || p.isDead()) return;
        Location loc = st.location();
        if (loc == null) loc = Bukkit.getWorlds().get(0).getSpawnLocation();
        teleportBypass(p, loc);
        if (immediate) {
            applyState(p);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyState(p), 2L);
        }
    }

    /** เรียกตอนผู้เล่นเข้าเซิร์ฟ / เกิดใหม่ — ถ้ามีสถานะค้างจะคืนให้ */
    public void restoreIfPending(Player p) {
        UUID id = p.getUniqueId();
        if (byPlayer.containsKey(id)) return;
        PlayerState st = states.get(id);
        if (st == null) {
            File f = stateFile(id);
            if (f.exists()) {
                st = PlayerState.load(f);
                if (st != null) states.put(id, st);
            }
        }
        if (st == null) return;
        returnPlayer(p, true);
    }

    public boolean hasPendingState(UUID id) {
        return states.containsKey(id);
    }

    // ------------------------------------------------------------------ duel lifecycle

    public void startDuel(Player a, Player b, Kit kit) {
        requests.remove(a.getUniqueId());
        requests.remove(b.getUniqueId());
        launch(List.of(a), a.getName(), List.of(b), b.getName(), kit, false);
    }

    private void tell(List<Player> players, String msg) {
        for (Player p : players) Msg.send(p, msg);
    }

    private void launch(List<Player> ta, String nameA, List<Player> tb, String nameB, Kit kit, boolean party) {
        List<Player> all = new ArrayList<>(ta);
        all.addAll(tb);
        for (Player p : all) {
            if (isBusy(p)) {
                tell(all, "&cมีผู้เล่นกำลังอยู่ในดวลอยู่");
                return;
            }
        }
        World w = ensureWorld();
        if (w == null) {
            tell(all, "&cไม่พบโลก PvP &e" + pvpWorldName() + " &c(ให้แอดมินสร้างด้วย /mv create " + pvpWorldName() + " normal)");
            plugin.getLogger().warning("ไม่พบโลก '" + pvpWorldName() + "' — สร้างด้วย Multiverse: /mv create " + pvpWorldName() + " normal");
            return;
        }
        Arena arena = pickArena(w);
        if (arena == null) {
            tell(all, "&cสนามดวลเต็มทุกสนาม รอสักครู่แล้วลองใหม่");
            return;
        }

        for (Player p : all) p.closeInventory();

        Duel d = new Duel(ta, nameA, tb, nameB, kit, arena, party);
        assignSpawns(d);
        for (Player p : all) byPlayer.put(p.getUniqueId(), d);
        duels.add(d);
        usedArenas.add(arena.name());

        try {
            for (Player p : all) saveState(p);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "เซฟสถานะผู้เล่นไม่สำเร็จ ยกเลิกดวล", ex);
            abort(d, "&cเซฟข้อมูลผู้เล่นไม่สำเร็จ ยกเลิกการดวลเพื่อความปลอดภัย");
            return;
        }

        boolean ok = true;
        for (Player p : all) {
            if (!teleportBypass(p, d.spawnOf(p.getUniqueId()))) ok = false;
        }
        if (!ok) {
            abort(d, "&cวาปไปสนามไม่สำเร็จ (ถูกปลั๊กอินอื่นบล็อก)");
            return;
        }

        for (Player p : all) {
            if (party) {
                Msg.box(p, "&b&l  กำลังเข้าสู่สนามดวลปาตี้...",
                        "&7  " + nameA + " &c⚔ &7" + nameB,
                        "&7  " + ta.size() + " vs " + tb.size() + " &8| &7KIT: " + kit.displayName());
            } else {
                Msg.box(p, "&b&l  กำลังเข้าสู่สนามดวล...", "&7  KIT: " + kit.displayName());
            }
        }
        // หน่วง 2 tick ให้ปลั๊กอินจัดการโลก (เช่น Multiverse) ทำงานเสร็จก่อนใส่ kit
        Bukkit.getScheduler().runTaskLater(plugin, () -> beginCountdown(d), 2L);
    }

    private void abort(Duel d, String reason) {
        for (UUID id : d.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.send(p, reason);
        }
        d.phase = Duel.Phase.ENDED;
        finish(d, false);
    }

    private void prepare(Player p, Kit kit) {
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        p.setFlying(false);
        kit.apply(p);
        p.setAbsorptionAmount(0);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setLevel(0);
        p.setExp(0f);
    }

    private List<Player> onlineMembers(Duel d) {
        List<Player> out = new ArrayList<>();
        for (UUID id : d.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    private void beginCountdown(Duel d) {
        if (d.phase != Duel.Phase.COUNTDOWN) return;
        List<Player> now = onlineMembers(d);
        if (now.size() != d.members().size()) {
            // มีคนหลุดระหว่างเตรียม (handleQuit จะตัดสินผลให้แล้ว ถ้ายังไม่จบ = ยกเลิก)
            d.phase = Duel.Phase.ENDED;
            finish(d, false);
            return;
        }
        try {
            for (Player p : now) prepare(p, d.kit);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "ใส่ kit ไม่สำเร็จ", ex);
            abort(d, "&cเกิดข้อผิดพลาดตอนใส่ kit ยกเลิกการดวล");
            return;
        }
        final int total = Math.max(1, plugin.getConfig().getInt("countdown-seconds", 5));
        final int[] left = {total};
        d.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (d.phase != Duel.Phase.COUNTDOWN) {
                if (d.countdownTask != null) d.countdownTask.cancel();
                return;
            }
            List<Player> ps = onlineMembers(d);
            if (left[0] <= 0) {
                d.phase = Duel.Phase.FIGHTING;
                d.countdownTask.cancel();
                d.countdownTask = null;
                for (Player p : ps) {
                    Msg.title(p, "&c&l⚔ FIGHT ⚔", "&7ลุยเลย!", 0, 25, 10);
                    Msg.sound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f);
                }
                int max = Math.max(30, plugin.getConfig().getInt("duel-time-seconds", 600));
                d.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> end(d, Duel.DRAW), max * 20L);
                return;
            }
            String color = left[0] <= 1 ? "&c" : left[0] <= 3 ? "&e" : "&a";
            for (Player p : ps) {
                Msg.title(p, color + "&l" + left[0], "&7KIT: " + d.kit.displayName(), 0, 25, 5);
                Msg.sound(p, Sound.BLOCK_NOTE_BLOCK_PLING, left[0] <= 1 ? 2f : 1f);
            }
            left[0]--;
        }, 0L, 20L);
    }

    /**
     * ตัดผู้เล่นออกจากการต่อสู้ (ตาย/ยอมแพ้/ออกเซิร์ฟ) — ถ้าทีมใดไม่เหลือคนจะจบดวลทันที
     */
    public void eliminate(Duel d, UUID id) {
        if (d.phase == Duel.Phase.ENDED) return;
        if (!d.alive.remove(id)) return;
        int w = d.winnerTeam();
        if (w != Duel.ONGOING) {
            end(d, w);
            return;
        }
        // ยังสู้ต่อ (ดวลทีม) — แจ้งทุกคนในดวล
        int ta = d.aliveCount(0), tb = d.aliveCount(1);
        for (Player p : onlineMembers(d)) {
            Msg.send(p, "&c" + d.names.getOrDefault(id, "?") + " &7ถูกกำจัด! &8(เหลือ &f" + ta + " &8vs &f" + tb + "&8)");
        }
    }

    /** เรียกจาก listener ตอนผู้เล่นตายในดวล */
    public void handleDeath(Player victim) {
        Duel d = byPlayer.get(victim.getUniqueId());
        if (d == null) return;
        eliminate(d, victim.getUniqueId());
    }

    /** จบดวล winnerTeam: 0/1 = ทีมที่ชนะ, Duel.DRAW = เสมอ/หมดเวลา */
    public void end(Duel d, int winnerTeam) {
        if (d.phase == Duel.Phase.ENDED) return;
        d.phase = Duel.Phase.ENDED;
        d.cancelTasks();

        if (winnerTeam == 0 || winnerTeam == 1) {
            String wn = d.teamName(winnerTeam);
            String ln = d.teamName(1 - winnerTeam);
            for (UUID id : d.team(winnerTeam)) {
                Player w = Bukkit.getPlayer(id);
                if (w == null) continue;
                Msg.title(w, "&a&l★ VICTORY ★", "&7คุณชนะ &f" + ln, 5, 60, 15);
                Msg.sound(w, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f);
                Msg.box(w, "&a&l  คุณชนะการดวล!", "&7  คู่ต่อสู้: &f" + ln, "&7  KIT: " + d.kit.displayName());
            }
            for (UUID id : d.team(1 - winnerTeam)) {
                Player l = Bukkit.getPlayer(id);
                if (l == null) continue;
                Msg.title(l, "&c&l✖ DEFEAT ✖", "&7คุณแพ้ &f" + wn, 5, 60, 15);
                Msg.box(l, "&c&l  คุณแพ้การดวล", "&7  ผู้ชนะ: &f" + wn, "&7  KIT: " + d.kit.displayName());
            }
            if (plugin.getConfig().getBoolean("broadcast-results", true)) {
                Bukkit.broadcastMessage(Msg.PREFIX + Msg.c("&b" + wn + " &7เอาชนะ &c" + ln + " &7ใน KIT " + d.kit.displayName()));
            }
        } else {
            for (Player p : onlineMembers(d)) {
                Msg.title(p, "&e&l= DRAW =", "&7ผลเสมอ", 5, 60, 15);
                Msg.send(p, "&eผลเสมอ");
            }
        }
        long delay = Math.max(10, plugin.getConfig().getInt("end-delay-ticks", 80));
        d.finishTask = Bukkit.getScheduler().runTaskLater(plugin, () -> finish(d, false), delay);
    }

    public void forfeit(Player p) {
        Duel d = byPlayer.get(p.getUniqueId());
        if (d == null || d.phase == Duel.Phase.ENDED) {
            Msg.send(p, "&cคุณไม่ได้อยู่ในดวล");
            return;
        }
        if (!d.alive.contains(p.getUniqueId())) {
            Msg.send(p, "&7คุณถูกกำจัดไปแล้ว รอดวลจบ");
            return;
        }
        Msg.send(p, "&eคุณยอมแพ้");
        // ดวลทีม: ผู้ยอมแพ้ดูต่อเป็นสเปกเตเตอร์จนจบดวล
        p.setGameMode(GameMode.SPECTATOR);
        eliminate(d, p.getUniqueId());
    }

    /** ผู้เล่นออกจากเซิร์ฟ: ตัดออกจากดวล (ทีมที่เหลือชนะถ้าอีกทีมหมด) ส่วนของของคนออกจะถูกคืนตอนเข้าใหม่ (จากไฟล์) */
    public void handleQuit(Player p) {
        UUID id = p.getUniqueId();
        requests.remove(id);
        requests.values().removeIf(r -> !r.party() && r.target().equals(id));
        clickCd.remove(id);
        Duel d = byPlayer.get(id);
        if (d == null) return;
        if (d.phase != Duel.Phase.ENDED) {
            eliminate(d, id);
        }
        byPlayer.remove(id);
    }

    /** เอนทิตีนี้อยู่ใกล้สนามของดวลอื่นที่ยังเล่นอยู่มากกว่าสนามของ self หรือไม่ (กันเคลียร์ของดวลอื่นผิดอัน) */
    private boolean nearerToOtherDuel(Duel self, Location loc) {
        Location mine = self.arena.center();
        if (mine.getWorld() == null || !mine.getWorld().equals(loc.getWorld())) return false;
        double myDist = loc.distanceSquared(mine);
        for (Duel o : duels) {
            if (o == self || o.finished) continue;
            Location oc = o.arena.center();
            if (oc.getWorld() == null || !oc.getWorld().equals(loc.getWorld())) continue;
            if (loc.distanceSquared(oc) < myDist) return true;
        }
        return false;
    }

    private void finish(Duel d, boolean immediate) {
        if (d.finished) return;
        d.finished = true;
        d.phase = Duel.Phase.ENDED;
        d.cancelTasks();

        // คืนสนาม
        for (BlockState st : d.changed.values()) {
            try {
                st.update(true, false);
            } catch (Exception ignored) {
                // chunk ไม่โหลด ฯลฯ
            }
        }
        d.changed.clear();

        // เคลียร์ของที่เหลือในสนาม (ไม่แตะของที่อยู่ใกล้สนามของดวลอื่นที่ยังเล่นอยู่)
        try {
            Location c = d.arena.center();
            if (c.getWorld() != null) {
                double r = Math.max(10, plugin.getConfig().getDouble("cleanup-radius", 60));
                for (Entity e : c.getWorld().getNearbyEntities(c, r, 320, r)) {
                    if (e instanceof Player) continue;
                    if (e instanceof Item || e instanceof Projectile || e instanceof EnderCrystal
                            || e instanceof AreaEffectCloud || e instanceof ExperienceOrb
                            || e instanceof TNTPrimed || e instanceof FallingBlock) {
                        if (nearerToOtherDuel(d, e.getLocation())) continue;
                        e.remove();
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "เคลียร์เอนทิตีไม่สำเร็จ", ex);
        }

        // วาปผู้เล่นกลับ
        for (UUID id : d.members()) {
            if (byPlayer.get(id) == d) byPlayer.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) returnPlayer(p, immediate);
        }
        duels.remove(d);
        usedArenas.remove(d.arena.name());
    }

    /** ปิดเซิร์ฟ/รีโหลดปลั๊กอิน: จบทุกดวลและคืนของให้ทุกคนทันที */
    public void shutdown() {
        if (expiryTask != null) expiryTask.cancel();
        for (Duel d : new ArrayList<>(duels)) {
            d.cancelTasks();
            d.phase = Duel.Phase.ENDED;
            finish(d, true);
        }
        requests.clear();
        byPlayer.clear();
        duels.clear();
        usedArenas.clear();
    }
}
