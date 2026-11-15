# ChaosTimer — Хаос-таймер для Purpur 1.21.11

Каждые 3 минуты (настраивается) на bossbar заканчивается таймер, крутится рулетка
и случается случайное событие. Всё со звуками и титрами.

## Сборка
Нужны JDK 21 и Maven:

    mvn clean package

Готовый файл: `target/ChaosTimer-1.0.0.jar` → положить в `plugins/` сервера Purpur 1.21.11.

## Игра
    /chaos start            — начать (интервал из config.yml, 180 сек)
    /chaos modifier random 2 — добавить 2 случайных модификатора (лучше ДО старта)
    /chaos events           — список событий (id для /chaos event [id])
    /chaos help             — все команды

Права: `chaos.admin` (по умолчанию — операторы).
Опасные события (tnt, mlg, lightning) можно отключить в `disabled-events` в config.yml.
Для теста в одиночку включи `include-creative: true`, события «обмен» требуют 2+ игроков.
