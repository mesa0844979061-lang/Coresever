package com.kitduel;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class DuelListener implements Listener {
    private final KitDuelPlugin plugin;
    private final DuelManager m;

    public DuelListener(KitDuelPlugin plugin, DuelManager m) {
        this.plugin = plugin;
        this.m = m;
    }

    private boolean inPvpWorld(org.bukkit.World w) {
        return w != null && w.getName().equals(m.pvpWorldName());
    }

    // ------------------------------------------------------------ sword + GUI

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof Player target)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean solo = Items.isDuelSword(hand);
        boolean party = Items.isPartySword(hand);
        if (!solo && !party) return;
        e.setCancelled(true);
        if (target.hasMetadata("NPC")) return;
        if (m.duelOf(p) != null) return; // ระหว่างดวลไม่มีดาบอยู่แล้ว กันไว้เฉยๆ
        if (party) {
            m.handlePartySwordClick(p, target);
        } else {
            m.handleSwordClick(p, target);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof KitGui.Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
        int slot = e.getRawSlot();
        if (slot == KitGui.CLOSE_SLOT) {
            Msg.sound(p, Sound.UI_BUTTON_CLICK, 1f);
            Bukkit.getScheduler().runTask(plugin, p::closeInventory);
            return;
        }
        int idx = KitGui.indexOf(slot);
        if (idx < 0 || idx >= m.kits().all().size()) return;
        Kit kit = m.kits().all().get(idx);
        Msg.sound(p, Sound.UI_BUTTON_CLICK, 1.5f);
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.closeInventory();
            if (h.party) {
                m.sendPartyChallenge(p, h.target, kit);
            } else {
                m.sendChallenge(p, h.target, kit);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGuiDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof KitGui.Holder) e.setCancelled(true);
    }

    // ------------------------------------------------------------ join / quit / respawn

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            m.restoreIfPending(p);
            if (m.duelOf(p) == null) {
                m.parties().syncSword(p); // ดาบปาตี้ค้างจากก่อนออกเซิร์ฟ -> เก็บคืนถ้าไม่ได้เป็นหัวหน้า
                if (plugin.getConfig().getBoolean("give-sword-on-join", true)) Items.ensureSword(plugin, p);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        m.handleQuit(e.getPlayer());
        m.parties().onQuit(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        Duel d = m.duelOf(v);
        if (d == null) {
            e.getDrops().removeIf(Items::isMenuSword);
            return;
        }
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.setKeepLevel(true);
        e.setDeathMessage(null);
        m.handleDeath(v); // ตัดออกจากดวล (ดวลทีม: ทีมไหนหมดก่อนแพ้)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (v.isOnline() && v.isDead()) v.spigot().respawn();
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Duel d = m.duelOf(p);
        if (d != null) {
            Location loc = d.spawnOf(p.getUniqueId());
            if (loc.getWorld() != null) e.setRespawnLocation(loc);
            // คนที่ตาย/ยอมแพ้ (ไม่อยู่ใน alive) ดูต่อเป็นสเปกเตเตอร์จนจบดวล
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && m.duelOf(p) == d && !d.alive.contains(p.getUniqueId())) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
            }, 2L);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            m.restoreIfPending(p);
            if (m.duelOf(p) == null) {
                m.parties().syncSword(p);
                if (plugin.getConfig().getBoolean("give-sword-on-join", true)) Items.ensureSword(plugin, p);
            }
        }, 3L);
    }

    // ------------------------------------------------------------ damage

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Duel d = m.duelOf(v);
        if (d != null && d.phase != Duel.Phase.FIGHTING) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Entity damager = e.getDamager();
        Player attacker = null;
        if (damager instanceof Player pl) {
            attacker = pl;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player pl) {
            attacker = pl;
        }
        if (attacker == null) return;
        Duel vd = m.duelOf(v);
        Duel ad = m.duelOf(attacker);
        // ดาบเมนู (ดาบท้าดวล/ดาบปาตี้) ใช้ตีคนนอกดวลไม่ได้
        if (ad == null && damager instanceof Player && Items.isMenuSword(attacker.getInventory().getItemInMainHand())) {
            e.setCancelled(true);
            return;
        }
        if ((vd != null || ad != null) && vd != ad) {
            e.setCancelled(true);
            return;
        }
        // ดวลทีม: เพื่อนร่วมทีมไม่ทำดาเมจกัน (รวมลูกธนู/ตรีศูล)
        if (vd != null && !v.equals(attacker) && vd.sameTeam(v.getUniqueId(), attacker.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ restrictions during duel

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Duel d = m.duelOf(e.getPlayer());
        if (d == null || d.phase != Duel.Phase.COUNTDOWN) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Duel d = m.duelOf(e.getPlayer());
        if (d != null && d.phase == Duel.Phase.COUNTDOWN) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (m.duelOf(e.getPlayer()) != null || Items.isMenuSword(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (m.duelOf(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (m.duelOf(p) == null || m.isBypass(p)) return;
        switch (e.getCause()) {
            case COMMAND, PLUGIN, SPECTATE, UNKNOWN -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (m.duelOf(p) == null || p.hasPermission("kitduel.admin")) return;
        String msg = e.getMessage();
        if (msg.length() < 2) return;
        String base = msg.substring(1).split(" ")[0].toLowerCase();
        int colon = base.indexOf(':');
        if (colon >= 0) base = base.substring(colon + 1);
        List<String> allowed = plugin.getConfig().getStringList("allowed-commands");
        for (String a : allowed) if (a.equalsIgnoreCase(base)) return;
        e.setCancelled(true);
        Msg.send(p, "&cใช้คำสั่งนี้ระหว่างดวลไม่ได้ (ยอมแพ้: &f/duel leave&c)");
    }

    // ------------------------------------------------------------ blocks

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Duel d = m.duelOf(e.getPlayer());
        if (d == null) return;
        if (d.phase != Duel.Phase.FIGHTING || !m.track(d, e.getBlock(), e.getBlockReplacedState())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Duel d = m.duelOf(e.getPlayer());
        if (d == null) return;
        if (d.phase != Duel.Phase.FIGHTING || !d.changed.containsKey(e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Duel d = m.duelOf(e.getPlayer());
        if (d == null) return;
        if (d.phase != Duel.Phase.FIGHTING || !m.track(d, e.getBlock(), e.getBlock().getState())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Duel d = m.duelOf(e.getPlayer());
        if (d == null) return;
        if (d.phase != Duel.Phase.FIGHTING || !m.track(d, e.getBlock(), e.getBlock().getState())) {
            e.setCancelled(true);
        }
    }

    /** น้ำ/ลาวาที่ไหลต่อจากบล็อกที่ผู้เล่นวาง ต้องบันทึกไว้คืนสนามด้วย */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (!inPvpWorld(e.getBlock().getWorld())) return;
        Duel d = m.duelTracking(e.getBlock());
        if (d == null) return;
        if (!m.track(d, e.getToBlock(), e.getToBlock().getState())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (inPvpWorld(e.getEntity().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (inPvpWorld(e.getBlock().getWorld())) e.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (inPvpWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (inPvpWorld(e.getBlock().getWorld())) e.setCancelled(true);
    }
}
