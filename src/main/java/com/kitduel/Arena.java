package com.kitduel;

import org.bukkit.Location;

public record Arena(String name, Location spawn1, Location spawn2) {
    public Location center() {
        return new Location(spawn1.getWorld(),
                (spawn1.getX() + spawn2.getX()) / 2.0,
                (spawn1.getY() + spawn2.getY()) / 2.0,
                (spawn1.getZ() + spawn2.getZ()) / 2.0);
    }
}
