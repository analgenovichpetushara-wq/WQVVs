package com.p3win.data.entity;

/**
 * Достижение/веха главного экрана P3WIN.
 * Например: «10 часов в библиотеке», «первый запуск 32-битной игры».
 */
public class Milestone {

    public long id;

    /** Стабильный ключ (не переводится), по нему ищется запись. */
    public String key = "";

    public String title = "";

    public String description = "";

    public boolean achieved;

    public long achievedAt;

    /** 0..100 — прогресс для недостигнутых вех. */
    public int progress;
}
