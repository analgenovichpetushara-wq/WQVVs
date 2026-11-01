package com.p3win.data.entity;

/**
 * Игра в библиотеке P3WIN.
 *
 * <p>Игра ≠ контейнер. Один контейнер может содержать много игр, одна игра
 * привязана к конкретному контейнеру (там лежит её wineprefix-окружение
 * и, при необходимости, файлы). Это разделение — базовое требование проекта.
 */
public class Game {

    public long id;

    public String title = "";

    /** Контейнер ядра, в котором живёт игра. */
    public long containerId;

    /** Путь внутри Wine, например {@code C:\Games\Quake\quake.exe}. */
    public String executable = "";

    public String arguments = "";

    /** Имя файла ярлыка {@code .desktop} внутри контейнера. */
    public String shortcutName = "";

    public String tags = "";

    public String description = "";

    public int releaseYear;

    /** Профиль P3 (game_profiles.id), 0 — использовать профиль контейнера. */
    public long profileId;

    public boolean favorite;

    public boolean hidden;

    public long totalPlaytimeMs;

    public long lastPlayedAt;

    public long addedAt;

    /** Имена файлов обложек внутри {@code files/p3win/library/<id>/}. */
    public String posterFile = "";

    public String coverFile = "";

    public String heroFile = "";

    /** 0..5 звёзд. */
    public int rating;

    /** 0 — не проверено, 1 — идеально, 2 — играбельно, 3 — проблемы. */
    public int compatibility;
}
