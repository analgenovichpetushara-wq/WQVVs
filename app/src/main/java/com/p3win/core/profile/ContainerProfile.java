package com.p3win.core.profile;

/**
 * Готовый профиль контейнера (Performance / Balanced / Compatibility / Legacy / Custom).
 *
 * <p>Профиль — пресет значений, который применяется к контейнеру ядра при создании
 * и может быть в любой момент переопределён вручную. Мы не храним настройки в двух
 * местах: после применения профиль остаётся лишь «меткой» в базе P3.
 */
public final class ContainerProfile {

    public final String id;
    public final String title;
    public final String description;

    /** Пресет Box64 (см. {@code com.winlator.box64.Box64Preset}). */
    public final String box64Preset;

    /** Разрешение экрана, например {@code 1280x720}. */
    public final String screenSize;

    /** Режим HUD ядра: 0 disabled, 1 simple, 2 full (порядок FrameRating.Mode). */
    public final int hudMode;

    /** Startup selection: 0 normal, 1 essential, 2 aggressive. */
    public final int startupSelection;

    /** Дополнительные переменные окружения вида {@code KEY=VALUE KEY2=VALUE2}. */
    public final String extraEnvVars;

    /** Настройки DXVK (KeyValueSet-строка: version, framerate, maxDeviceMemory...). */
    public final String dxWrapperConfig;

    /** Vulkan-драйвер, который профиль ставит по умолчанию. */
    public final String vulkanDriver;

    /** Полезно для игр 2000-х: ограничение расширений Mesa. */
    public final boolean legacyFriendly;

    ContainerProfile(String id, String title, String description, String box64Preset,
                     String screenSize, int hudMode, int startupSelection,
                     String extraEnvVars, String dxWrapperConfig, String vulkanDriver,
                     boolean legacyFriendly) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.box64Preset = box64Preset;
        this.screenSize = screenSize;
        this.hudMode = hudMode;
        this.startupSelection = startupSelection;
        this.extraEnvVars = extraEnvVars;
        this.dxWrapperConfig = dxWrapperConfig;
        this.vulkanDriver = vulkanDriver;
        this.legacyFriendly = legacyFriendly;
    }
}
