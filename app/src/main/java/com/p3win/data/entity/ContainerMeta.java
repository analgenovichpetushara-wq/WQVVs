package com.p3win.data.entity;

/**
 * Метаданные контейнера P3WIN.
 *
 * <p>Настройки самого контейнера (разрешение, драйверы, пресет Box64) живут
 * в JSON ядра — это единственный источник правды. Здесь только то, чего у ядра
 * нет: профиль P3, избранное, заметки и статистика использования.
 *
 * <p>{@link #id} совпадает с {@code com.winlator.container.Container.id},
 * поэтому связь между слоями не требует отдельной таблицы соответствий.
 */
public class ContainerMeta {

    public long id;

    /** Имя на момент последней синхронизации (для сортировки без обращения к ядру). */
    public String name = "";

    /** Идентификатор профиля P3: performance/balanced/compatibility/legacy/custom. */
    public String profileId = "balanced";

    public String box64Preset = "PERFORMANCE";

    public String wineVersion = "";

    public String graphicsDriver = "turnip";

    public String note = "";

    public int accentColor;

    public boolean favorite;

    public long createdAt;

    public long lastUsedAt;

    public int launchCount;

    public long totalPlaytimeMs;

    public long sizeBytes;
}
