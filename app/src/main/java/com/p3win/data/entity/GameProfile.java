package com.p3win.data.entity;

/**
 * Профиль запуска игры — оверрайды поверх контейнера.
 *
 * <p>Применяются <b>до</b> старта сессии: P3 собирает итоговые параметры
 * (драйвер, DXVK-конфиг, переменные Box64, раскладку ввода) и передаёт их
 * ядру через extras интента. Контейнер при этом не перезаписывается —
 * поэтому переключение профилей мгновенное и обратимое.
 */
public class GameProfile {

    public long id;

    public String name = "";

    /** Пусто — взять из контейнера. */
    public String graphicsDriver = "";

    public String graphicsDriverConfig = "";

    public String dxWrapper = "";

    public String dxWrapperConfig = "";

    public String screenSize = "";

    public String box64Preset = "";

    public String envVars = "";

    /** 0 — без ограничения. */
    public int fpsLimit;

    public boolean fsrEnabled;

    /** 0..4, где 4 — максимальная резкость AMD FSR. */
    public int fsrSharpness = 2;

    public int controlsProfileId;

    /** GamepadHandler.DINPUT_MAPPER_TYPE_STANDARD(0) / XINPUT(1). */
    public int dinputMapperType = 1;

    /** Раскладка P3 (input_layouts.id), 0 — глобальная. */
    public String inputLayout = "";

    public boolean forceFullscreen = true;

    public String audioDriver = "";

    public String audioDriverConfig = "";

    /**
     * Аргументы командной строки, дописываемые к запуску.
     * Ядро читает их из ярлыка как {@code execArgs}.
     */
    public String arguments = "";

    public long createdAt;

    public long updatedAt;
}
