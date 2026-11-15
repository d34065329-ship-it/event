package me.chaos;

/** Модификаторы — правила, действующие всю игру. */
public enum Modifier {

    FAST("fast", "<red>★ Спринт", "Таймер вдвое короче — события идут вдвое чаще"),
    DOUBLE("double", "<light_purple>★ Двойной хаос", "Каждый раз срабатывают сразу два события подряд"),
    LOW_GRAVITY("moon", "<aqua>★ Лунная гравитация", "Низкая гравитация — прыгаешь как на Луне"),
    NO_FALL("nofall", "<green>★ Мягкая посадка", "Урон от падения отключён"),
    LUCKY("lucky", "<gold>★ Лакиблоки", "Из любого сломанного блока падает случайный предмет (иногда — ловушка!)"),
    VAMPIRE("vampire", "<dark_red>★ Вампиры", "Ударил игрока или моба — вылечился на 30% нанесённого урона"),
    GLASS("glass", "<white>★ Стеклянные пушки", "Всего 5 сердец, но урон ×2"),
    TURBO("turbo", "<yellow>★ Турбо", "Скорость передвижения +40%"),
    GLOW("glow", "<yellow>★ Подсветка", "Все игроки светятся сквозь стены"),
    BOOM("boom", "<red>★ Взрывные мобы", "Убитые мобы взрываются (блоки не ломаются)");

    private final String id;
    private final String title;
    private final String description;

    Modifier(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }

    public static Modifier byId(String id) {
        for (Modifier m : values()) {
            if (m.id.equalsIgnoreCase(id)) return m;
        }
        return null;
    }
}
