package me.chaos;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ChaosCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of(
            "start", "stop", "pause", "skip", "time", "interval", "event", "events", "modifier", "reload", "help");
    private static final List<String> MOD_SUB = List.of("list", "add", "remove", "clear", "random");

    private final ChaosPlugin plugin;
    private final ChaosGame game;

    public ChaosCommand(ChaosPlugin plugin, ChaosGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                int sec = a.length > 1 ? num(a[1]) : 0;
                if (a.length > 1 && sec < 5) {
                    s.sendMessage(Fx.mm("<red>Интервал — число секунд, минимум 5."));
                    return true;
                }
                if (!game.start(sec)) s.sendMessage(Fx.mm("<red>Игра уже идёт."));
            }
            case "stop" -> {
                if (!game.isRunning()) s.sendMessage(Fx.mm("<red>Игра не запущена."));
                else game.stop();
            }
            case "pause" -> {
                if (!game.togglePause()) s.sendMessage(Fx.mm("<red>Игра не запущена."));
            }
            case "skip" -> {
                if (!game.skip()) s.sendMessage(Fx.mm("<red>Игра не запущена."));
            }
            case "time" -> {
                int sec = a.length > 1 ? num(a[1]) : -1;
                if (!game.isRunning() || sec < 1) s.sendMessage(Fx.mm("<red>/chaos time [секунды] — только во время игры."));
                else game.setRemaining(sec);
            }
            case "interval" -> {
                int sec = a.length > 1 ? num(a[1]) : -1;
                if (sec < 5) s.sendMessage(Fx.mm("<red>/chaos interval [секунды, минимум 5]"));
                else {
                    game.setInterval(sec);
                    s.sendMessage(Fx.mm("<green>Интервал: <white>" + sec + " сек <gray>(применится со следующего цикла)"));
                }
            }
            case "event" -> {
                ChaosEvent ev = a.length > 1 ? game.registry().byId(a[1]) : null;
                if (ev == null) s.sendMessage(Fx.mm("<red>/chaos event [id] — список: /chaos events"));
                else game.forceEvent(ev);
            }
            case "events" -> {
                s.sendMessage(Fx.mm("<gold>События (" + game.registry().all().size() + "):"));
                for (ChaosEvent e : game.registry().all()) {
                    s.sendMessage(Fx.mm("<dark_gray>[<yellow>" + e.id() + "<dark_gray>] " + e.name() + " <dark_gray>— <gray>" + e.description()));
                }
            }
            case "modifier", "mod" -> modifierCmd(s, a);
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage(Fx.mm("<green>Конфиг перезагружен."));
            }
            default -> help(s);
        }
        return true;
    }

    private void modifierCmd(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                s.sendMessage(Fx.mm("<gold>Модификаторы:"));
                for (Modifier m : Modifier.values()) {
                    s.sendMessage(Fx.mm((game.modifiers().has(m) ? "<green>✔ " : "<gray>✖ ")
                            + m.title() + " <dark_gray>[" + m.id() + "] <gray>" + m.description()));
                }
            }
            case "add", "remove" -> {
                Modifier m = a.length > 2 ? Modifier.byId(a[2]) : null;
                if (m == null) {
                    s.sendMessage(Fx.mm("<red>Неизвестный модификатор. Список: /chaos modifier list"));
                    return;
                }
                if (sub.equals("add")) game.addModifier(m);
                else game.removeModifier(m);
                if (!game.isRunning()) s.sendMessage(Fx.mm("<gray>Модификаторы начнут действовать после /chaos start."));
            }
            case "clear" -> {
                for (Modifier m : new ArrayList<>(game.modifiers().active())) game.removeModifier(m);
            }
            case "random" -> {
                int n = a.length > 2 ? Math.max(1, num(a[2])) : 1;
                List<Modifier> free = new ArrayList<>();
                for (Modifier m : Modifier.values()) if (!game.modifiers().has(m)) free.add(m);
                Collections.shuffle(free, game.rnd());
                for (int i = 0; i < Math.min(n, free.size()); i++) game.addModifier(free.get(i));
            }
            default -> s.sendMessage(Fx.mm("<red>/chaos modifier [list|add|remove|clear|random]"));
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(Fx.mm("<gold><bold>Хаос-таймер</bold> <gray>— команды:"));
        String[] lines = {
                "/chaos start [секунды] <gray>— начать игру",
                "/chaos stop <gray>— остановить",
                "/chaos pause <gray>— пауза / продолжить",
                "/chaos skip <gray>— сразу запустить событие",
                "/chaos time [сек] <gray>— поставить оставшееся время",
                "/chaos interval [сек] <gray>— интервал между событиями",
                "/chaos event [id] <gray>— запустить конкретное событие",
                "/chaos events <gray>— список событий",
                "/chaos modifier [list|add|remove|clear|random] [id/число]",
                "/chaos reload <gray>— перезагрузить конфиг"
        };
        for (String l : lines) s.sendMessage(Fx.mm("<yellow>" + l));
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(ROOT);
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "event" -> game.registry().all().forEach(e -> out.add(e.id()));
                case "modifier", "mod" -> out.addAll(MOD_SUB);
                default -> { }
            }
        } else if (a.length == 3 && a[0].toLowerCase(Locale.ROOT).startsWith("mod")) {
            String sub = a[1].toLowerCase(Locale.ROOT);
            if (sub.equals("add") || sub.equals("remove")) {
                for (Modifier m : Modifier.values()) out.add(m.id());
            }
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
