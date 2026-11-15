package me.chaos;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Location;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Random;

public final class Util {

    private Util() {}

    /** Ищет безопасную точку на земле в кольце minR..maxR вокруг центра. */
    public static Location near(Location c, double minR, double maxR, Random r) {
        World w = c.getWorld();
        for (int attempt = 0; attempt < 12; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = minR + r.nextDouble() * (maxR - minR);
            int x = c.getBlockX() + (int) Math.round(Math.cos(ang) * rad);
            int z = c.getBlockZ() + (int) Math.round(Math.sin(ang) * rad);
            for (int y = c.getBlockY() + 3; y >= c.getBlockY() - 4; y--) {
                if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 2) continue;
                Block feet = w.getBlockAt(x, y, z);
                Block head = feet.getRelative(0, 1, 0);
                Block below = feet.getRelative(0, -1, 0);
                if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !below.isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        return c.clone();
    }

    /** Помечает сущность: её взрыв не ломает блоки. */
    public static void noGrief(ChaosPlugin plugin, Entity e) {
        if (plugin.getConfig().getBoolean("protect-blocks", true)) {
            e.getPersistentDataContainer().set(plugin.noGriefKey(), PersistentDataType.BYTE, (byte) 1);
        }
    }

    public static boolean hasMod(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : inst.getModifiers()) {
            if (m.getKey().equals(key)) return true;
        }
        return false;
    }

    public static void removeMod(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
            if (m.getKey().equals(key)) inst.removeModifier(m);
        }
    }

    public static void giveOrDrop(Player p, ItemStack item) {
        p.getInventory().addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }

    /** Кладёт предмет в хотбар и делает слот активным. */
    public static void giveHotbar(Player p, ItemStack item) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                inv.setItem(i, item);
                inv.setHeldItemSlot(i);
                return;
            }
        }
        int held = inv.getHeldItemSlot();
        ItemStack old = inv.getItem(held);
        inv.setItem(held, item);
        if (old != null && !old.getType().isAir()) giveOrDrop(p, old);
    }
}
