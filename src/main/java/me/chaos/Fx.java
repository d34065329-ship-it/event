package me.chaos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Мелкие помощники: звуки, титры, частицы, MiniMessage. */
public final class Fx {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Fx() {}

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    /** Звук по строковому ключу (например "entity.player.levelup") — не зависит от версии enum Sound. */
    public static void sound(Player p, String key, float volume, float pitch) {
        p.playSound(p.getLocation(), key, SoundCategory.MASTER, volume, pitch);
    }

    public static void soundAll(String key, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) sound(p, key, volume, pitch);
    }

    public static void title(Player p, String title, String subtitle, int fadeInMs, int stayMs, int fadeOutMs) {
        p.showTitle(Title.title(mm(title), mm(subtitle),
                Title.Times.times(Duration.ofMillis(fadeInMs), Duration.ofMillis(stayMs), Duration.ofMillis(fadeOutMs))));
    }

    public static void burst(Location loc, Particle particle, int count, double spread) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(particle, loc, count, spread, spread, spread, 0.02);
    }

    public static void broadcast(String miniMessage) {
        Bukkit.getServer().broadcast(mm(miniMessage));
    }
}
