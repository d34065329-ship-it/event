package me.chaos;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Игровой цикл: таймер на bossbar, рулетка событий, модификаторы. */
public final class ChaosGame {

    private static final class Pending {
        BukkitTask task;
        Runnable action;
    }

    private final ChaosPlugin plugin;
    private final ModifierManager modifiers;
    private final EventRegistry registry;
    private final Random random = new Random();
    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
    private final List<Pending> pending = new ArrayList<>();
    private final Map<UUID, Set<PotionEffectType>> given = new HashMap<>();

    private BukkitTask loop;
    private boolean running;
    private boolean paused;
    private int remaining;
    private int total;
    private int baseInterval;
    private String lastEventId = "";

    public ChaosGame(ChaosPlugin plugin) {
        this.plugin = plugin;
        this.modifiers = new ModifierManager(plugin);
        this.registry = new EventRegistry(plugin);
    }

    // ---------------------------------------------------------------- доступ

    public ChaosPlugin plugin() { return plugin; }
    public ModifierManager modifiers() { return modifiers; }
    public EventRegistry registry() { return registry; }
    public Random rnd() { return random; }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }

    /** Модификатор действует только пока игра идёт. */
    public boolean mod(Modifier m) { return running && modifiers.has(m); }

    // ---------------------------------------------------------------- управление

    public boolean start(int intervalSeconds) {
        if (running) return false;
        baseInterval = intervalSeconds > 0 ? intervalSeconds : plugin.getConfig().getInt("interval-seconds", 180);
        total = effectiveInterval();
        remaining = total;
        running = true;
        paused = false;
        updateBar();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            Fx.sound(p, "entity.ender_dragon.growl", 0.6f, 1.3f);
            Fx.sound(p, "ui.toast.challenge_complete", 1f, 1f);
            Fx.title(p, "<gradient:gold:red><bold>ХАОС-ТАЙМЕР</bold></gradient>",
                    "<gray>Событие каждые <yellow>" + fmt(total) + "</yellow>. Держитесь!", 300, 3000, 800);
        }
        Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <white>Игра началась! Следующее событие через <yellow>" + fmt(total));
        if (!modifiers.active().isEmpty()) {
            for (Modifier m : modifiers.active()) {
                Fx.broadcast("<dark_gray>  » " + m.title() + " <dark_gray>— <gray>" + m.description());
            }
        }
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void stop() {
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        boolean wasRunning = running;
        running = false;
        paused = false;
        revertAll();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
            modifiers.cleanup(p);
            Set<PotionEffectType> effects = given.get(p.getUniqueId());
            if (effects != null) for (PotionEffectType t : effects) p.removePotionEffect(t);
        }
        given.clear();
        if (wasRunning) {
            Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <red>Игра остановлена.");
            Fx.soundAll("block.beacon.deactivate", 1f, 1f);
        }
    }

    public boolean togglePause() {
        if (!running) return false;
        paused = !paused;
        Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] " + (paused ? "<yellow>Пауза." : "<green>Продолжаем!"));
        Fx.soundAll("block.note_block.bell", 1f, paused ? 0.6f : 1.4f);
        updateBar();
        return true;
    }

    public boolean skip() {
        if (!running) return false;
        remaining = total = effectiveInterval();
        trigger();
        return true;
    }

    public void setRemaining(int seconds) {
        remaining = Math.max(1, seconds);
        if (remaining > total) total = remaining;
        updateBar();
    }

    public void setInterval(int seconds) {
        baseInterval = Math.max(5, seconds);
    }

    public void forceEvent(ChaosEvent ev) {
        announce(ev);
        execute(ev);
    }

    public void onJoin(Player p) {
        if (running) {
            p.showBossBar(bar);
            modifiers.refresh(p);
        }
    }

    // ---------------------------------------------------------------- модификаторы

    public void addModifier(Modifier m) {
        if (!modifiers.add(m)) return;
        Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <white>Модификатор: " + m.title() + " <dark_gray>— <gray>" + m.description());
        for (Player p : Bukkit.getOnlinePlayers()) {
            Fx.sound(p, "block.beacon.activate", 1f, 1.2f);
            Fx.title(p, m.title(), "<gray>" + m.description(), 200, 2500, 500);
        }
        if (running) modifiers.refreshAll();
    }

    public void removeModifier(Modifier m) {
        if (!modifiers.remove(m)) return;
        Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <gray>Модификатор снят: " + m.title());
        Fx.soundAll("block.beacon.deactivate", 1f, 1.2f);
        if (running) modifiers.refreshAll();
    }

    // ---------------------------------------------------------------- тик

    private int effectiveInterval() {
        int base = baseInterval > 0 ? baseInterval : plugin.getConfig().getInt("interval-seconds", 180);
        return modifiers.has(Modifier.FAST) ? Math.max(15, base / 2) : base;
    }

    private void tick() {
        modifiers.refreshAll();
        if (paused) {
            updateBar();
            return;
        }
        remaining--;
        if (remaining <= 0) {
            total = effectiveInterval();
            remaining = total;
            updateBar();
            trigger();
            return;
        }
        if (remaining <= 5) countdownBeep(remaining);
        updateBar();
    }

    private void countdownBeep(int sec) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sec <= 3) {
                Fx.sound(p, "block.note_block.pling", 1f, 0.8f + (3 - sec) * 0.35f);
                Fx.title(p, "<red><bold>" + sec, "<gray>приготовьтесь...", 0, 900, 100);
            } else {
                Fx.sound(p, "block.note_block.hat", 0.8f, 1.2f);
            }
        }
    }

    private void updateBar() {
        float progress = total <= 0 ? 0f : Math.max(0f, Math.min(1f, remaining / (float) total));
        bar.progress(progress);
        bar.color(remaining <= 10 ? BossBar.Color.RED : (progress < 0.4f ? BossBar.Color.YELLOW : BossBar.Color.GREEN));
        String mods = modifiers.active().isEmpty() ? ""
                : " <dark_gray>| <gray>модификаторов: <white>" + modifiers.active().size();
        String head = paused ? "<yellow><bold>ПАУЗА</bold>" : "<gradient:gold:red><bold>✦ ХАОС</bold></gradient>";
        bar.name(Fx.mm(head + " <white>через <yellow>" + fmt(remaining) + mods));
    }

    private static String fmt(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    // ---------------------------------------------------------------- события

    public List<Player> players() {
        boolean creative = plugin.getConfig().getBoolean("include-creative", false);
        List<Player> list = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (p.isDead() || gm == GameMode.SPECTATOR) continue;
            if (gm == GameMode.CREATIVE && !creative) continue;
            list.add(p);
        }
        return list;
    }

    public void trigger() {
        runEvents(modifiers.has(Modifier.DOUBLE) ? 2 : 1);
    }

    private void runEvents(int count) {
        List<Player> ps = players();
        if (ps.isEmpty()) return;
        ChaosEvent ev = pickEvent(ps.size());
        spin(0, 16, ev, () -> {
            execute(ev);
            if (count > 1) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (running) runEvents(count - 1);
                }, 60L);
            }
        });
    }

    private ChaosEvent pickEvent(int playerCount) {
        Set<String> disabled = new HashSet<>(plugin.getConfig().getStringList("disabled-events"));
        List<ChaosEvent> pool = new ArrayList<>(registry.available(playerCount, disabled));
        if (pool.isEmpty()) pool = new ArrayList<>(registry.all());
        if (pool.size() > 1) pool.removeIf(e -> e.id().equals(lastEventId));
        return pool.get(random.nextInt(pool.size()));
    }

    /** «Рулетка»: названия событий мелькают всё медленнее, затем выпадает результат. */
    private void spin(int step, int steps, ChaosEvent result, Runnable done) {
        if (!running) return;
        if (step >= steps) {
            announce(result);
            done.run();
            return;
        }
        ChaosEvent fake = registry.random(random);
        float pitch = Math.min(2f, 0.6f + step * 0.09f);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Fx.sound(p, "block.note_block.pling", 0.7f, pitch);
            Fx.title(p, "<yellow>» " + fake.name() + " <yellow>«", "<gray>крутим рулетку...", 0, 500, 0);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> spin(step + 1, steps, result, done), 2 + step / 4);
    }

    private void announce(ChaosEvent ev) {
        Component title = Fx.mm(ev.name());
        Component sub = Fx.mm("<gray>" + ev.description());
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(title, sub,
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(3200), Duration.ofMillis(700))));
            Fx.sound(p, "ui.toast.challenge_complete", 0.8f, 1f);
            Fx.sound(p, ev.soundKey(), 1f, ev.pitch());
        }
        Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <white>Событие: " + ev.name()
                + " <dark_gray>— <gray>" + ev.description());
    }

    private void execute(ChaosEvent ev) {
        lastEventId = ev.id();
        List<Player> ps = players();
        if (ps.isEmpty()) return;
        try {
            ev.action().run(this, ps);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Ошибка в событии " + ev.id(), ex);
        }
    }

    // ---------------------------------------------------------------- помощники для событий

    /** Выдаёт эффект и запоминает его, чтобы снять при остановке игры. */
    public void effect(Player p, PotionEffectType type, int seconds, int amplifier) {
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true, true));
        given.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(type);
    }

    /** Временный модификатор атрибута (снимется сам; не сохраняется в данных игрока). */
    public void tempAttribute(Player p, Attribute attribute, String keyName, double amount,
                              AttributeModifier.Operation op, long ticks) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "evt_" + keyName);
        Util.removeMod(inst, key);
        inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
        UUID id = p.getUniqueId();
        later(ticks, () -> {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null) return;
            AttributeInstance i = pl.getAttribute(attribute);
            if (i != null) Util.removeMod(i, key);
        });
    }

    /** Отложенное действие; при остановке игры выполняется досрочно (откат изменений). */
    public void later(long ticks, Runnable action) {
        Pending p = new Pending();
        p.action = action;
        p.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pending.remove(p);
            action.run();
        }, ticks);
        pending.add(p);
    }

    private void revertAll() {
        for (Pending p : new ArrayList<>(pending)) {
            p.task.cancel();
            try {
                p.action.run();
            } catch (Exception ignored) {
                // откат не должен ронять остановку
            }
        }
        pending.clear();
    }
}
