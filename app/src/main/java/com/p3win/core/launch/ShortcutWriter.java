package com.p3win.core.launch;

import com.p3win.core.log.P3Log;
import com.winlator.container.Container;
import com.winlator.container.Shortcut;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;

import java.io.File;

/**
 * Создание ярлыков {@code .desktop} для игр P3WIN.
 *
 * <h3>Зачем это нужно</h3>
 * В Winlator 11.2 ярлык — не просто «ссылка на exe». Именно из секции
 * {@code [Extra Data]} ядро берёт пофайловые настройки запуска:
 * {@code graphicsDriver, dxwrapper, dxwrapperConfig, screenSize, box64Preset,
 * envVars, execArgs, dinputMapperType, controlsProfile}.
 * Экстра-данные интента ядро <b>игнорирует</b> (проверено по
 * {@code XServerDisplayActivity}: читаются только {@code container_id},
 * {@code shortcut_path}, {@code exec_path}).
 *
 * <p>Следствие: чтобы профиль игры работал, P3 обязан положить его в ярлык.
 * Поэтому перед каждой сессией {@code GameLauncher} перезаписывает
 * {@code [Extra Data]} конкретного ярлыка — это и есть «применение оверрайдов
 * до старта сессии» из требований. Настройки контейнера при этом не меняются.
 *
 * <p>Формат файла (совместим с {@link Shortcut}):
 * <pre>
 * [Desktop Entry]
 * Name=...
 * Exec=wine "C:\\Games\\x.exe"
 * Icon=...
 *
 * [Extra Data]
 * key=value
 * ...
 * </pre>
 */
public final class ShortcutWriter {

    private static final String TAG = "Shortcut";
    private static final String SECTION_ENTRY = "[Desktop Entry]";
    private static final String SECTION_EXTRA = "[Extra Data]";

    private ShortcutWriter() {
    }

    /**
     * Записать профиль игры в секцию {@code [Extra Data]} ярлыка.
     *
     * <p>Существующие ключи перезаписываются только те, которые задаёт профиль;
     * остальные сохраняются как есть. Секция {@code [Desktop Entry]}
     * не изменяется вообще — иконка, имя и команда запуска остаются прежними.
     *
     * @param shortcut готовый ярлык ядра (файл должен существовать)
     * @param profile  профиль P3 (может быть null — тогда только чистка)
     * @return true, если файл записан
     */
    public static boolean applyProfile(Shortcut shortcut, com.p3win.data.entity.GameProfile profile) {
        if (shortcut == null || shortcut.file == null || !shortcut.file.isFile()) return false;
        if (profile == null) return true;

        try {
            if (notEmpty(profile.graphicsDriver)) shortcut.putExtra("graphicsDriver", profile.graphicsDriver);
            if (notEmpty(profile.graphicsDriverConfig)) {
                shortcut.putExtra("graphicsDriverConfig", profile.graphicsDriverConfig);
            }
            if (notEmpty(profile.dxWrapper)) shortcut.putExtra("dxwrapper", profile.dxWrapper);
            if (notEmpty(profile.dxWrapperConfig)) {
                shortcut.putExtra("dxwrapperConfig", dxWrapperConfigWithFps(profile));
            }
            if (notEmpty(profile.screenSize)) shortcut.putExtra("screenSize", profile.screenSize);
            if (notEmpty(profile.box64Preset)) shortcut.putExtra("box64Preset", profile.box64Preset);
            if (notEmpty(profile.audioDriver)) shortcut.putExtra("audioDriver", profile.audioDriver);
            if (notEmpty(profile.audioDriverConfig)) {
                shortcut.putExtra("audioDriverConfig", profile.audioDriverConfig);
            }
            if (notEmpty(profile.arguments)) shortcut.putExtra("execArgs", profile.arguments);

            String envVars = buildEnvVars(profile);
            if (!envVars.isEmpty()) shortcut.putExtra("envVars", envVars);

            if (profile.controlsProfileId > 0) {
                shortcut.putExtra("controlsProfile", String.valueOf(profile.controlsProfileId));
            }
            shortcut.putExtra("dinputMapperType", String.valueOf(profile.dinputMapperType));

            // forceFullscreen в ярлыке ядро не читает, но ключ безвреден и полезен
            // для совместимости с будущими версиями.
            shortcut.putExtra("forceFullscreen", profile.forceFullscreen ? "1" : "0");

            shortcut.saveData();
            P3Log.info(TAG, "Профиль «" + profile.name + "» записан в ярлык "
                    + shortcut.file.getName());
            return true;
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось записать профиль в ярлык", t);
            return false;
        }
    }

    /**
     * Создать ярлык для игры, у которой его ещё нет (добавление вручную).
     *
     * @param container контейнер ядра
     * @param name      отображаемое имя
     * @param dosPath   путь внутри Wine, например {@code C:\Games\a.exe}
     * @return созданный файл или null
     */
    public static File create(Container container, String name, String dosPath) {
        if (container == null || dosPath == null || dosPath.isEmpty()) return null;
        try {
            File desktopDir = new File(container.getUserDir(), "Desktop");
            if (!desktopDir.isDirectory() && !desktopDir.mkdirs()) return null;

            String safeName = name != null && !name.trim().isEmpty()
                    ? name.trim() : FileUtils.getBasename(dosPath);
            safeName = safeName.replaceAll("[\\\\/:*?\"<>|]", "_");
            File file = new File(desktopDir, safeName + ".desktop");
            if (file.exists()) {
                // Не перезаписываем чужой ярлык: добавляем суффикс.
                int counter = 2;
                while (file.exists()) {
                    file = new File(desktopDir, safeName + " (" + counter + ").desktop");
                    counter++;
                }
            }

            String content = SECTION_ENTRY + "\n"
                    + "Name=" + safeName + "\n"
                    + "Exec=wine \"" + dosPath + "\"\n"
                    + "Icon=" + safeName.toLowerCase().replace(' ', '-') + "\n"
                    + "\n"
                    + SECTION_EXTRA + "\n"
                    + "path=" + dosPath + "\n";

            FileUtils.writeString(file, content);
            FileUtils.chmod(file, 0771);
            P3Log.info(TAG, "Создан ярлык " + file.getPath());
            return file;
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось создать ярлык", t);
            return null;
        }
    }

    /** Прочитать текущие Extras ярлыка (для редактора профиля). */
    public static String readExtras(File shortcutFile) {
        if (shortcutFile == null || !shortcutFile.isFile()) return "";
        StringBuilder result = new StringBuilder();
        boolean inExtra = false;
        for (String line : FileUtils.readLines(shortcutFile, true)) {
            if (line.startsWith("[")) {
                inExtra = line.contains("Extra Data");
                continue;
            }
            if (inExtra) result.append(line).append('\n');
        }
        return result.toString().trim();
    }

    // ------------------------------------------------------------------

    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Собрать строку переменных окружения для ярлыка.
     *
     * <p>Ограничение FPS задаётся двумя путями: {@code DXVK_FRAME_RATE}
     * (работает для DXVK 2.x) и {@code framerate} в конфиге DXVK
     * (см. {@link #dxWrapperConfigWithFps}). Дублирование намеренное:
     * какая-то из игр может игнорировать один из механизмов.
     */
    static String buildEnvVars(com.p3win.data.entity.GameProfile profile) {
        EnvVars envVars = new EnvVars();
        if (notEmpty(profile.envVars)) envVars.putAll(profile.envVars);

        if (profile.fpsLimit > 0) {
            envVars.put("DXVK_FRAME_RATE", profile.fpsLimit);
        }
        if (profile.fsrEnabled) {
            envVars.put("WINE_FULLSCREEN_FSR", 1);
            envVars.put("WINE_FULLSCREEN_FSR_STRENGTH",
                    Math.max(0, Math.min(4, profile.fsrSharpness)));
        }
        return envVars.toString();
    }

    /** Конфиг DXVK с вписанным ограничением кадров (формат ядра). */
    static String dxWrapperConfigWithFps(com.p3win.data.entity.GameProfile profile) {
        com.winlator.core.KeyValueSet config =
                new com.winlator.core.KeyValueSet(profile.dxWrapperConfig);
        if (profile.fpsLimit > 0) config.put("framerate", profile.fpsLimit);
        return config.toString();
    }
}
