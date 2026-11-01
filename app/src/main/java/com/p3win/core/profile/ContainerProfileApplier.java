package com.p3win.core.profile;

import com.winlator.container.Container;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.EnvVars;
import com.winlator.core.KeyValueSet;

import org.json.JSONObject;

/**
 * Применение профиля P3WIN к контейнеру ядра.
 *
 * <p>Профиль не дублирует настройки: он заполняет поля {@link Container},
 * которыми ядро пользуется само. Форматы строго как у ядра:
 * <ul>
 *   <li>{@code graphicsDriver} — «Vulkan,OpenGL» через запятую;</li>
 *   <li>{@code graphicsDriverConfig} — два {@link KeyValueSet} через {@code |};</li>
 *   <li>{@code dxwrapperConfig} — {@link KeyValueSet} (version, framerate...);</li>
 *   <li>{@code envVars} — {@code KEY=VALUE} через пробел, профиль <b>дополняет</b>
 *       существующие переменные, а не заменяет их.</li>
 * </ul>
 */
public final class ContainerProfileApplier {

    private ContainerProfileApplier() {
    }

    /** Заполнить поля контейнера значениями профиля. */
    public static void apply(Container container, ContainerProfile profile) {
        if (container == null || profile == null) return;

        container.setBox64Preset(profile.box64Preset);
        container.setScreenSize(profile.screenSize);
        container.setHUDMode((byte) profile.hudMode);
        container.setStartupSelection((byte) profile.startupSelection);

        // Драйверы: Vulkan из профиля, OpenGL оставляем текущий.
        if (profile.vulkanDriver != null && !profile.vulkanDriver.isEmpty()) {
            String openGl = GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
            String current = container.getGraphicsDriver();
            if (current != null) {
                String[] parts = GraphicsDrivers.parseIdentifiers(current);
                if (parts.length > 1 && GraphicsDrivers.isOpenGLDriver(parts[1])) openGl = parts[1];
            }
            container.setGraphicsDriver(profile.vulkanDriver + "," + openGl);
        }

        if (profile.extraEnvVars != null && !profile.extraEnvVars.isEmpty()) {
            EnvVars envVars = new EnvVars(container.getEnvVars());
            envVars.putAll(profile.extraEnvVars);
            container.setEnvVars(envVars.toString());
        }

        if (profile.dxWrapperConfig != null && !profile.dxWrapperConfig.isEmpty()) {
            container.setDXWrapperConfig(new KeyValueSet(profile.dxWrapperConfig).toString());
        }
    }

    /**
     * JSON для {@code ContainerManager.createContainerAsync}.
     *
     * <p>Набор ключей повторяет штатный диалог создания контейнера ядра,
     * поэтому P3-контейнер неотличим от «родного».
     */
    public static JSONObject buildCreationData(ContainerProfile profile, String name,
                                               String wineVersionIdentifier) {
        try {
            JSONObject data = new JSONObject();
            data.put("name", name);
            data.put("screenSize", profile.screenSize);
            data.put("box64Preset", profile.box64Preset);
            data.put("hudMode", profile.hudMode);
            data.put("startupSelection", profile.startupSelection);

            String vulkan = profile.vulkanDriver != null && !profile.vulkanDriver.isEmpty()
                    ? profile.vulkanDriver : GraphicsDrivers.TURNIP;
            data.put("graphicsDriver", vulkan + "," + GraphicsDrivers.DEFAULT_OPENGL_DRIVER);

            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("desktopTheme", com.winlator.core.WineThemeManager.DEFAULT_DESKTOP_THEME);

            EnvVars envVars = new EnvVars(Container.DEFAULT_ENV_VARS);
            if (profile.extraEnvVars != null && !profile.extraEnvVars.isEmpty()) {
                envVars.putAll(profile.extraEnvVars);
            }
            data.put("envVars", envVars.toString());

            if (profile.dxWrapperConfig != null && !profile.dxWrapperConfig.isEmpty()) {
                data.put("dxwrapperConfig", new KeyValueSet(profile.dxWrapperConfig).toString());
            }

            if (wineVersionIdentifier != null && !wineVersionIdentifier.isEmpty()) {
                data.put("wineVersion", wineVersionIdentifier);
            }
            return data;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /**
     * Конфиг Vulkan-драйвера Turnip в формате ядра.
     *
     * <p>Ключи повторяют {@code TurnipConfigDialog}: {@code version},
     * {@code maxDeviceMemory}, {@code directRendering}, {@code presentMode}.
     * Иные ключи ядро просто проигнорирует.
     *
     * @param maxDeviceMemoryMb 0 — не ограничивать
     * @param presentMode       ordinal из {@code GPUHelper.VkPresentMode}
     *                          (0 FIFO, 1 MAILBOX — дефолт ядра, 2 IMMEDIATE)
     */
    public static String turnipConfig(String version, int maxDeviceMemoryMb, int presentMode) {
        KeyValueSet config = new KeyValueSet();
        config.put("version", version != null && !version.isEmpty()
                ? version : com.winlator.core.DefaultVersion.TURNIP);
        config.put("maxDeviceMemory", maxDeviceMemoryMb > 0 ? maxDeviceMemoryMb + "MB" : "0");
        config.put("directRendering", 1);
        config.put("presentMode", presentMode);
        return config.toString();
    }

    /** Конфиг Turnip, рассчитанный на Adreno 810 (Gen 8). */
    public static String adreno810TurnipConfig() {
        return turnipConfig(com.winlator.core.DefaultVersion.TURNIP, 0, 1);
    }
}
