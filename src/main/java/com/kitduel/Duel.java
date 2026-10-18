package com.kitduel;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** ดวลแบบทีม: ทีม A vs ทีม B (1v1 = ทีมละ 1 คน, ปาตี้ = ทีมละหลายคน) */
public final class Duel {
    public enum Phase { COUNTDOWN, FIGHTING, ENDED }

    public static final int ONGOING = -2;
    public static final int DRAW = -1;

    public final List<UUID> teamA;
    public final List<UUID> teamB;
    public final String nameA;
    public final String nameB;
    public final Map<UUID, String> names = new HashMap<>();
    public final Map<UUID, Location> spawns = new HashMap<>();
    /** คนที่ยังไม่ตาย/ยังไม่ยอมแพ้ */
    public final Set<UUID> alive = new HashSet<>();
    public final Kit kit;
    public final Arena arena;
    public final boolean party;
    public volatile Phase phase = Phase.COUNTDOWN;
    public boolean finished = false;
    public final Map<Block, BlockState> changed = new HashMap<>();
    public BukkitTask countdownTask;
    public BukkitTask timeoutTask;
    public BukkitTask finishTask;

    public Duel(List<Player> a, String nameA, List<Player> b, String nameB, Kit kit, Arena arena, boolean party) {
        this.teamA = new ArrayList<>();
        this.teamB = new ArrayList<>();
        for (Player p : a) {
            teamA.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
        for (Player p : b) {
            teamB.add(p.getUniqueId());
            names.put(p.getUniqueId(), p.getName());
        }
        alive.addAll(teamA);
        alive.addAll(teamB);
        this.nameA = nameA;
        this.nameB = nameB;
        this.kit = kit;
        this.arena = arena;
        this.party = party;
    }

    public List<UUID> members() {
        List<UUID> all = new ArrayList<>(teamA);
        all.addAll(teamB);
        return all;
    }

    public List<UUID> team(int t) {
        return t == 0 ? teamA : teamB;
    }

    public String teamName(int t) {
        return t == 0 ? nameA : nameB;
    }

    /** 0 = ทีม A, 1 = ทีม B, -1 = ไม่ได้อยู่ในดวลนี้ */
    public int teamOf(UUID id) {
        if (teamA.contains(id)) return 0;
        if (teamB.contains(id)) return 1;
        return -1;
    }

    public boolean sameTeam(UUID x, UUID y) {
        int tx = teamOf(x);
        return tx != -1 && tx == teamOf(y);
    }

    public int aliveCount(int t) {
        int n = 0;
        for (UUID id : team(t)) if (alive.contains(id)) n++;
        return n;
    }

    /** ONGOING = ยังสู้อยู่, DRAW = ตายพร้อมกันทั้งสองทีม, 0/1 = ทีมที่ชนะ */
    public int winnerTeam() {
        int a = aliveCount(0), b = aliveCount(1);
        if (a == 0 && b == 0) return DRAW;
        if (a == 0) return 1;
        if (b == 0) return 0;
        return ONGOING;
    }

    public Location spawnOf(UUID id) {
        Location l = spawns.get(id);
        if (l == null) l = (teamOf(id) == 1 ? arena.spawn2() : arena.spawn1());
        return l.clone();
    }

    public void cancelTasks() {
        if (countdownTask != null) countdownTask.cancel();
        if (timeoutTask != null) timeoutTask.cancel();
        if (finishTask != null) finishTask.cancel();
        countdownTask = null;
        timeoutTask = null;
        finishTask = null;
    }
}
