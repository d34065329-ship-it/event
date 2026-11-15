package me.chaos;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Хранит активные модификаторы и поддерживает их атрибуты у игроков.
 * Используются transient-модификаторы атрибутов: они не сохраняются в данных игрока,
 * поэтому после краша/выхода ничего «навсегда» не залипает.
 */
public final class ModifierManager {

    private record AttrMod(Attribute attribute, String suffix, double amount, Operation op) {}

    private final ChaosPlugin plugin;
    private final Set<Modifier> active = EnumSet.noneOf(Modifier.class);
    private final Map<Modifier, List<AttrMod>> attrs = new EnumMap<>(Modifier.class);

    public ModifierManager(ChaosPlugin plugin) {
        this.plugin = plugin;
        attrs.put(Modifier.LOW_GRAVITY, List.of(
                new AttrMod(Attribute.GRAVITY, "grav", -0.7, Operation.ADD_SCALAR)));
        attrs.put(Modifier.GLASS, List.of(
                new AttrMod(Attribute.MAX_HEALTH, "hp", -0.5, Operation.ADD_SCALAR),
                new AttrMod(Attribute.ATTACK_DAMAGE, "dmg", 1.0, Operation.MULTIPLY_SCALAR_1)));
        attrs.put(Modifier.TURBO, List.of(
                new AttrMod(Attribute.MOVEMENT_SPEED, "spd", 0.4, Operation.MULTIPLY_SCALAR_1)));
    }

    public Set<Modifier> active() { return Collections.unmodifiableSet(active); }

    public boolean has(Modifier m) { return active.contains(m); }

    public boolean add(Modifier m) { return active.add(m); }

    public boolean remove(Modifier m) {
        boolean removed = active.remove(m);
        if (removed && m == Modifier.GLOW) {
            for (Player p : Bukkit.getOnlinePlayers()) p.removePotionEffect(PotionEffectType.GLOWING);
        }
        return removed;
    }

    public void clear() {
        for (Modifier m : new ArrayList<>(active)) remove(m);
    }

    private NamespacedKey key(Modifier m, AttrMod am) {
        return new NamespacedKey(plugin, "mod_" + m.id() + "_" + am.suffix());
    }

    /** Приводит атрибуты игрока в соответствие с активными модификаторами (идемпотентно). */
    public void refresh(Player p) {
        for (Map.Entry<Modifier, List<AttrMod>> e : attrs.entrySet()) {
            boolean on = active.contains(e.getKey());
            for (AttrMod am : e.getValue()) {
                AttributeInstance inst = p.getAttribute(am.attribute());
                if (inst == null) continue;
                NamespacedKey k = key(e.getKey(), am);
                boolean has = Util.hasMod(inst, k);
                if (on && !has) {
                    inst.addTransientModifier(new AttributeModifier(k, am.amount(), am.op(), EquipmentSlotGroup.ANY));
                } else if (!on && has) {
                    Util.removeMod(inst, k);
                }
            }
        }
        if (active.contains(Modifier.GLASS)) {
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
        }
        if (active.contains(Modifier.GLOW)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, false));
        }
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    /** Полностью убирает следы модификаторов у игрока (конец игры). */
    public void cleanup(Player p) {
        for (Map.Entry<Modifier, List<AttrMod>> e : attrs.entrySet()) {
            for (AttrMod am : e.getValue()) {
                AttributeInstance inst = p.getAttribute(am.attribute());
                if (inst != null) Util.removeMod(inst, key(e.getKey(), am));
            }
        }
        if (active.contains(Modifier.GLOW)) p.removePotionEffect(PotionEffectType.GLOWING);
    }
}
