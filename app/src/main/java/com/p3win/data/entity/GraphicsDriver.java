package com.p3win.data.entity;

/**
 * Запись о GPU-драйвере для P3 GPU Manager.
 *
 * <p>Каталог драйверов у ядра — это файл {@code index.txt} в
 * {@code installed_components/turnip|vortek}, плюс сами .tzst-архивы в assets.
 * Здесь хранится то, что нужно менеджеру: какие версии реально установлены,
 * какая активна, чем она рискованна для Adreno 810 и была ли она проверена
 * на эталонном устройстве.
 */
public class GraphicsDriver {

    public long id;

    /** Идентификатор ядра: turnip / vortek / zink / virgl / gladio. */
    public String driverId = "";

    public String version = "";

    public String displayName = "";

    public String vendor = "";

    /** Совместимость по модели GPU: 0 — не ограничено. */
    public int minAdrenoModel;

    public int maxAdrenoModel;

    /** Заявлена поддержка Adreno Gen 8 (8xx) — критично для Adreno 810. */
    public boolean supportsGen8;

    /** Проверено на эталонном устройстве (realme P3 5G, SD 6 Gen 4). */
    public boolean verifiedOnReference;

    /** 0 — неизвестно, 1 — работает, 2 — с оговорками, 3 — не работает. */
    public int compatibilityLevel;

    /** Поставляется с APK (можно переустановить из assets). */
    public boolean builtin;

    public boolean installed;

    public boolean active;

    public long installedAt;

    public String warning = "";
}
