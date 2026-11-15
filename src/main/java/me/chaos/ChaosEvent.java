package me.chaos;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Одно событие хаоса.
 *
 * @param name        название (MiniMessage)
 * @param description краткое описание
 * @param soundKey    звук при срабатывании
 * @param minPlayers  минимум игроков, чтобы событие имело смысл
 */
public record ChaosEvent(String id, String name, String description, String soundKey,
                         float pitch, int minPlayers, Action action) {

    @FunctionalInterface
    public interface Action {
        void run(ChaosGame game, List<Player> players);
    }
}
