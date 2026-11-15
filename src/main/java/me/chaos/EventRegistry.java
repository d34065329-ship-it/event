package me.chaos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/** Все события хаоса. Добавить своё — один вызов add(...) в register(). */
public final class EventRegistry {

    private final ChaosPlugin plugin;
    private final List<ChaosEvent> events = new ArrayList<>();

    public EventRegistry(ChaosPlugin plugin) {
        this.plugin = plugin;
        register();
    }

    public List<ChaosEvent> all() { return Collections.unmodifiableList(events); }

    public ChaosEvent byId(String id) {
        for (ChaosEvent e : events) if (e.id().equalsIgnoreCase(id)) return e;
        return null;
    }

    public ChaosEvent random(Random r) { return events.get(r.nextInt(events.size())); }

    public List<ChaosEvent> available(int players, Collection<String> disabled) {
        List<ChaosEvent> list = new ArrayList<>();
        for (ChaosEvent e : events) {
            if (e.minPlayers() <= players && !disabled.contains(e.id())) list.add(e);
        }
        return list;
    }

    private void add(String id, String name, String desc, String sound, float pitch, int min, ChaosEvent.Action action) {
        events.add(new ChaosEvent(id, name, desc, sound, pitch, min, action));
    }

    // ------------------------------------------------------------------ утилиты

    private <T extends Mob> void swarm(ChaosGame g, List<Player> ps, Class<T> type, int perPlayer, Consumer<T> extra) {
        for (Player p : ps) {
            for (int i = 0; i < perPlayer; i++) {
                Location l = Util.near(p.getLocation(), 4, 8, g.rnd());
                T mob = p.getWorld().spawn(l, type);
                mob.setRemoveWhenFarAway(true);
                mob.setTarget(p);
                extra.accept(mob);
                Fx.burst(l.clone().add(0, 1, 0), Particle.SOUL_FIRE_FLAME, 25, 0.3);
            }
        }
    }

    private static boolean overworld(World w) {
        return w.getEnvironment() == World.Environment.NORMAL;
    }

    // ------------------------------------------------------------------ события

    private void register() {

        add("swap", "<light_purple>★ Телепорт-рулетка", "Все меняются местами по кругу!",
                "entity.enderman.teleport", 0.7f, 2, (g, ps) -> {
                    List<Player> sh = new ArrayList<>(ps);
                    Collections.shuffle(sh);
                    List<Location> locs = new ArrayList<>();
                    for (Player p : sh) locs.add(p.getLocation());
                    for (int i = 0; i < sh.size(); i++) {
                        Player p = sh.get(i);
                        Location to = locs.get((i + 1) % sh.size()).clone();
                        to.setYaw(p.getLocation().getYaw());
                        to.setPitch(p.getLocation().getPitch());
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.PORTAL, 80, 0.5);
                        p.teleport(to);
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 80, 0.5);
                        Fx.sound(p, "entity.enderman.teleport", 1f, 1.2f);
                    }
                });

        add("invswap", "<gold>★ Инвентарная лотерея", "Все инвентари передаются по кругу!",
                "entity.illusioner.mirror_move", 1f, 2, (g, ps) -> {
                    List<Player> sh = new ArrayList<>(ps);
                    Collections.shuffle(sh);
                    List<ItemStack[]> contents = new ArrayList<>();
                    for (Player p : sh) contents.add(p.getInventory().getContents().clone());
                    for (int i = 0; i < sh.size(); i++) {
                        Player p = sh.get(i);
                        p.getInventory().setContents(contents.get((i + 1) % sh.size()));
                        p.updateInventory();
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.ENCHANT, 60, 0.6);
                        Fx.sound(p, "entity.item.pickup", 1f, 0.7f);
                    }
                });

        add("levitation", "<aqua>★ Левитация", "Вы взлетаете на 30 секунд!",
                "entity.shulker.shoot", 1f, 1, (g, ps) -> {
                    boolean safe = g.plugin().getConfig().getBoolean("safe-levitation", true);
                    for (Player p : ps) {
                        g.effect(p, PotionEffectType.LEVITATION, 30, 0);
                        if (safe) g.effect(p, PotionEffectType.SLOW_FALLING, 40, 0);
                        Fx.burst(p.getLocation(), Particle.CLOUD, 40, 0.5);
                    }
                });

        add("launch", "<gold>★ Катапульта", "Вас подбросило высоко в небо!",
                "entity.firework_rocket.launch", 0.8f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        p.setVelocity(new Vector(g.rnd().nextDouble() - 0.5, 1.6, g.rnd().nextDouble() - 0.5));
                        g.effect(p, PotionEffectType.SLOW_FALLING, 15, 0);
                        Fx.burst(p.getLocation(), Particle.FIREWORK, 40, 0.3);
                    }
                });

        add("zombies", "<dark_green>★ Нашествие зомби", "Вокруг каждого появились зомби!",
                "entity.zombie.ambient", 0.7f, 1, (g, ps) ->
                        swarm(g, ps, Zombie.class, 3, z -> {
                            z.setShouldBurnInDay(false);
                            z.customName(Fx.mm("<red>Сюрприз"));
                            z.setCustomNameVisible(true);
                        }));

        add("creepers", "<green>★ Крипер-вечеринка", "К вам ползут криперы. Тсссс...",
                "entity.creeper.primed", 1f, 1, (g, ps) ->
                        swarm(g, ps, Creeper.class, 2, c -> Util.noGrief(plugin, c)));

        add("tnt", "<red>★ Дождь из TNT", "Бегите! Динамит с неба!",
                "entity.tnt.primed", 1f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        for (int i = 0; i < 3; i++) {
                            Location l = p.getLocation().add(g.rnd().nextInt(11) - 5, 10 + g.rnd().nextInt(4), g.rnd().nextInt(11) - 5);
                            int fuse = 50 + g.rnd().nextInt(30);
                            p.getWorld().spawn(l, TNTPrimed.class, t -> {
                                t.setFuseTicks(fuse);
                                Util.noGrief(plugin, t);
                            });
                        }
                    }
                });

        add("animals", "<yellow>★ Мясной дождь", "С неба падают животные!",
                "entity.chicken.ambient", 0.8f, 1, (g, ps) -> {
                    EntityType[] types = {EntityType.CHICKEN, EntityType.PIG, EntityType.COW, EntityType.SHEEP};
                    for (Player p : ps) {
                        for (int i = 0; i < 10; i++) {
                            Location l = p.getLocation().add(g.rnd().nextInt(9) - 4, 14, g.rnd().nextInt(9) - 4);
                            Entity e = p.getWorld().spawnEntity(l, types[g.rnd().nextInt(types.length)]);
                            if (e instanceof Mob m) m.setRemoveWhenFarAway(true);
                            if (e instanceof Sheep s) s.customName(Component.text("jeb_")); // радужная овечка
                        }
                    }
                });

        add("blind", "<dark_gray>★ Выключили свет", "Слепота на 8 секунд!",
                "entity.elder_guardian.curse", 1f, 1, (g, ps) -> {
                    for (Player p : ps) g.effect(p, PotionEffectType.BLINDNESS, 8, 0);
                });

        add("loot", "<green>★ Подарок судьбы", "Каждый получает по два случайных предмета!",
                "entity.villager.celebrate", 1f, 1, (g, ps) -> {
                    List<ItemStack> pool = List.of(
                            new ItemStack(Material.GOLDEN_APPLE, 2), new ItemStack(Material.DIAMOND, 2),
                            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), new ItemStack(Material.COOKED_BEEF, 16),
                            new ItemStack(Material.ARROW, 32), new ItemStack(Material.IRON_INGOT, 8),
                            new ItemStack(Material.EXPERIENCE_BOTTLE, 8), new ItemStack(Material.ENDER_PEARL, 3),
                            new ItemStack(Material.TOTEM_OF_UNDYING, 1), new ItemStack(Material.TNT, 4),
                            new ItemStack(Material.COBWEB, 6), new ItemStack(Material.OBSIDIAN, 4),
                            new ItemStack(Material.FIREWORK_ROCKET, 16), new ItemStack(Material.WATER_BUCKET, 1),
                            new ItemStack(Material.LAVA_BUCKET, 1), new ItemStack(Material.SHIELD, 1),
                            new ItemStack(Material.COOKED_PORKCHOP, 12), new ItemStack(Material.GOLD_INGOT, 6));
                    for (Player p : ps) {
                        for (int i = 0; i < 2; i++) {
                            ItemStack item = pool.get(g.rnd().nextInt(pool.size())).clone();
                            Util.giveOrDrop(p, item);
                            p.sendMessage(Component.text("+ ", NamedTextColor.GREEN)
                                    .append(Component.text(item.getAmount() + "× ", NamedTextColor.WHITE))
                                    .append(Component.translatable(item.getType().translationKey(), NamedTextColor.YELLOW)));
                        }
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 30, 0.6);
                        Fx.sound(p, "entity.player.levelup", 1f, 1.2f);
                    }
                });

        add("hunger", "<dark_red>★ Голодомор", "Живот урчит: голод на 30 секунд!",
                "entity.ravager.roar", 1.2f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        g.effect(p, PotionEffectType.HUNGER, 30, 1);
                        p.setFoodLevel(Math.min(p.getFoodLevel(), 6));
                    }
                });

        add("feast", "<green>★ Пир горой", "Сытость и регенерация для всех!",
                "entity.player.burp", 1f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        p.setFoodLevel(20);
                        p.setSaturation(20f);
                        g.effect(p, PotionEffectType.REGENERATION, 10, 1);
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.HAPPY_VILLAGER, 30, 0.6);
                    }
                });

        add("lightning", "<yellow>★ Гнев Зевса", "Молния бьёт рядом с каждым!",
                "entity.lightning_bolt.thunder", 1f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        Location l = p.getLocation().add(g.rnd().nextInt(5) - 2, 0, g.rnd().nextInt(5) - 2);
                        p.getWorld().strikeLightningEffect(l);
                        double dmg = Math.min(6.0, p.getHealth() - 2.0);
                        if (dmg > 0) p.damage(dmg);
                        p.setFireTicks(60);
                    }
                });

        add("jumper", "<green>★ Кузнечики", "Супер-прыжки и скорость на 30 секунд!",
                "entity.rabbit.jump", 0.8f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        g.effect(p, PotionEffectType.JUMP_BOOST, 30, 4);
                        g.effect(p, PotionEffectType.SPEED, 30, 1);
                        Fx.burst(p.getLocation(), Particle.CLOUD, 30, 0.4);
                    }
                });

        add("size", "<light_purple>★ Гиганты и малыши", "Одни выросли, другие уменьшились — на 45 секунд!",
                "entity.puffer_fish.blow_up", 0.7f, 1, (g, ps) -> {
                    List<Player> sh = new ArrayList<>(ps);
                    Collections.shuffle(sh);
                    for (int i = 0; i < sh.size(); i++) {
                        Player p = sh.get(i);
                        boolean giant = i % 2 == 0;
                        g.tempAttribute(p, Attribute.SCALE, "scale", giant ? 0.6 : -0.6, Operation.ADD_SCALAR, 45 * 20L);
                        if (giant) {
                            g.effect(p, PotionEffectType.STRENGTH, 45, 1);
                            g.effect(p, PotionEffectType.SLOWNESS, 45, 0);
                            Fx.sound(p, "entity.puffer_fish.blow_up", 1f, 0.6f);
                            p.sendMessage(Fx.mm("<gold>Ты — ГИГАНТ! Сила II, но ты медленнее."));
                        } else {
                            g.effect(p, PotionEffectType.SPEED, 45, 1);
                            g.effect(p, PotionEffectType.JUMP_BOOST, 45, 1);
                            Fx.sound(p, "entity.puffer_fish.blow_out", 1f, 1.5f);
                            p.sendMessage(Fx.mm("<aqua>Ты — малыш! Скорость и прыжки."));
                        }
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.POOF, 40, 0.5);
                    }
                });

        add("time", "<blue>★ Смена времени", "День становится ночью (с грозой) — и наоборот!",
                "entity.wolf.howl", 1f, 1, (g, ps) -> {
                    Set<World> worlds = new HashSet<>();
                    for (Player p : ps) if (overworld(p.getWorld())) worlds.add(p.getWorld());
                    for (World w : worlds) {
                        long t = w.getTime();
                        boolean night = t >= 13000 && t < 23000;
                        if (night) {
                            w.setTime(1000);
                            w.setStorm(false);
                            w.setThundering(false);
                        } else {
                            w.setTime(14000);
                            w.setStorm(true);
                            w.setThundering(true);
                        }
                    }
                });

        add("freeze", "<aqua>★ Ледниковый период", "Вы вмёрзли в лёд на 5 секунд (но вы неуязвимы)!",
                "entity.player.hurt_freeze", 1f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        g.effect(p, PotionEffectType.SLOWNESS, 5, 6);
                        g.effect(p, PotionEffectType.JUMP_BOOST, 5, 250);
                        g.effect(p, PotionEffectType.RESISTANCE, 5, 4);
                        p.setFreezeTicks(p.getMaxFreezeTicks());
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.SNOWFLAKE, 60, 0.6);
                    }
                });

        add("target", "<red>★ Охота!", "Один игрок светится — остальные его ловят!",
                "entity.wither.ambient", 0.8f, 2, (g, ps) -> {
                    Player t = ps.get(g.rnd().nextInt(ps.size()));
                    g.effect(t, PotionEffectType.GLOWING, 40, 0);
                    g.effect(t, PotionEffectType.ABSORPTION, 40, 2);
                    g.effect(t, PotionEffectType.STRENGTH, 40, 1);
                    Fx.sound(t, "entity.warden.heartbeat", 1f, 1f);
                    t.sendMessage(Fx.mm("<red>Ты — мишень! Продержись 40 секунд."));
                    for (Player o : ps) {
                        if (o.equals(t)) continue;
                        g.effect(o, PotionEffectType.SPEED, 40, 1);
                        Fx.sound(o, "entity.wolf.growl", 1f, 1f);
                    }
                    Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <red>Охота на <yellow>" + t.getName() + "<red>! Скорость II у охотников.");
                });

        add("mlg", "<aqua>★ MLG-вызов", "Вас подняли на 30 блоков над землёй. Ведро воды в руке — не промахнись!",
                "entity.phantom.swoop", 0.8f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        World w = p.getWorld();
                        if (!overworld(w)) {
                            g.effect(p, PotionEffectType.LEVITATION, 5, 0);
                            continue;
                        }
                        Location loc = p.getLocation();
                        int y = Math.min(w.getMaxHeight() - 2, w.getHighestBlockYAt(loc) + 30);
                        p.teleport(new Location(w, loc.getX(), y, loc.getZ(), loc.getYaw(), 70f));
                        Util.giveHotbar(p, new ItemStack(Material.WATER_BUCKET));
                        Fx.burst(p.getLocation(), Particle.CLOUD, 40, 0.5);
                    }
                });

        add("cobweb", "<white>★ Паутинная ловушка", "Вы опутаны паутиной! Она рассосётся через 10 секунд.",
                "entity.spider.ambient", 0.8f, 1, (g, ps) -> {
                    List<Block> placed = new ArrayList<>();
                    for (Player p : ps) {
                        Block c = p.getLocation().getBlock();
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dy = 0; dy <= 1; dy++) {
                                for (int dz = -1; dz <= 1; dz++) {
                                    Block b = c.getRelative(dx, dy, dz);
                                    if (b.getType().isAir()) {
                                        b.setType(Material.COBWEB, false);
                                        placed.add(b);
                                    }
                                }
                            }
                        }
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.POOF, 30, 0.5);
                    }
                    g.later(200L, () -> {
                        for (Block b : placed) if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
                    });
                });

        add("potions", "<dark_purple>★ Зелье-лотерея", "Каждому — два случайных эффекта на 30 секунд!",
                "block.brewing_stand.brew", 1f, 1, (g, ps) -> {
                    List<Map.Entry<PotionEffectType, String>> pool = List.of(
                            Map.entry(PotionEffectType.SPEED, "Скорость"),
                            Map.entry(PotionEffectType.SLOWNESS, "Замедление"),
                            Map.entry(PotionEffectType.STRENGTH, "Сила"),
                            Map.entry(PotionEffectType.WEAKNESS, "Слабость"),
                            Map.entry(PotionEffectType.REGENERATION, "Регенерация"),
                            Map.entry(PotionEffectType.NAUSEA, "Тошнота"),
                            Map.entry(PotionEffectType.HASTE, "Спешка"),
                            Map.entry(PotionEffectType.MINING_FATIGUE, "Усталость"),
                            Map.entry(PotionEffectType.JUMP_BOOST, "Прыгучесть"),
                            Map.entry(PotionEffectType.INVISIBILITY, "Невидимость"),
                            Map.entry(PotionEffectType.NIGHT_VISION, "Ночное зрение"),
                            Map.entry(PotionEffectType.GLOWING, "Свечение"),
                            Map.entry(PotionEffectType.LEVITATION, "Левитация"),
                            Map.entry(PotionEffectType.RESISTANCE, "Сопротивление"),
                            Map.entry(PotionEffectType.FIRE_RESISTANCE, "Огнестойкость"));
                    for (Player p : ps) {
                        List<Map.Entry<PotionEffectType, String>> sh = new ArrayList<>(pool);
                        Collections.shuffle(sh, g.rnd());
                        for (int i = 0; i < 2; i++) {
                            Map.Entry<PotionEffectType, String> e = sh.get(i);
                            g.effect(p, e.getKey(), 30, 1);
                            p.sendMessage(Fx.mm("<light_purple>Эффект: <white>" + e.getValue() + " II"));
                        }
                        Fx.sound(p, "entity.witch.drink", 1f, 1f);
                    }
                });

        add("healthswap", "<red>★ Обмен здоровьем", "Здоровье и голод передаются по кругу!",
                "entity.evoker.prepare_summon", 1f, 2, (g, ps) -> {
                    List<Player> sh = new ArrayList<>(ps);
                    Collections.shuffle(sh);
                    int n = sh.size();
                    double[] hp = new double[n];
                    int[] food = new int[n];
                    for (int i = 0; i < n; i++) {
                        hp[i] = sh.get(i).getHealth();
                        food[i] = sh.get(i).getFoodLevel();
                    }
                    for (int i = 0; i < n; i++) {
                        Player p = sh.get(i);
                        double max = p.getAttribute(Attribute.MAX_HEALTH) != null
                                ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                        p.setHealth(Math.max(1.0, Math.min(max, hp[(i + 1) % n])));
                        p.setFoodLevel(food[(i + 1) % n]);
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.HEART, 10, 0.5);
                    }
                });

        add("xp", "<green>★ Урожай опыта", "Вы получили случайное количество уровней!",
                "entity.experience_orb.pickup", 0.8f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        int lv = 3 + g.rnd().nextInt(6);
                        p.giveExpLevels(lv);
                        p.sendMessage(Fx.mm("<green>+" + lv + " уровней опыта!"));
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.ENCHANT, 60, 0.6);
                        Fx.sound(p, "entity.player.levelup", 1f, 1f);
                    }
                });

        add("moon", "<aqua>★ Лунная гравитация", "Низкая гравитация на 30 секунд!",
                "block.beacon.activate", 1.3f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        g.tempAttribute(p, Attribute.GRAVITY, "moon", -0.75, Operation.ADD_SCALAR, 30 * 20L);
                        g.effect(p, PotionEffectType.SLOW_FALLING, 35, 0);
                        Fx.burst(p.getLocation(), Particle.END_ROD, 40, 0.5);
                    }
                });

        add("ghost", "<gray>★ Режим призрака", "Невидимость на 40 секунд (броня всё равно видна!)",
                "entity.vex.ambient", 0.8f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        g.effect(p, PotionEffectType.INVISIBILITY, 40, 0);
                        Fx.burst(p.getLocation().add(0, 1, 0), Particle.POOF, 40, 0.5);
                    }
                });

        add("fireworks", "<gold>★ Салют!", "Праздничный салют и регенерация на 10 секунд!",
                "entity.firework_rocket.twinkle", 1f, 1, (g, ps) -> {
                    for (Player p : ps) {
                        g.effect(p, PotionEffectType.REGENERATION, 10, 1);
                        for (int i = 0; i < 3; i++) {
                            Location l = p.getLocation().add(g.rnd().nextInt(7) - 3, 9, g.rnd().nextInt(7) - 3);
                            Firework fw = p.getWorld().spawn(l, Firework.class, f -> {
                                FireworkMeta meta = f.getFireworkMeta();
                                meta.addEffect(FireworkEffect.builder()
                                        .with(FireworkEffect.Type.BALL_LARGE)
                                        .withColor(Color.RED, Color.YELLOW, Color.LIME)
                                        .withFade(Color.ORANGE)
                                        .trail(true)
                                        .build());
                                meta.setPower(1);
                                f.setFireworkMeta(meta);
                            });
                            Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 4L);
                        }
                    }
                });
    }
}
