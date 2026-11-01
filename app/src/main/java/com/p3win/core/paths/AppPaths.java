package com.p3win.core.paths;

import android.content.Context;

import java.io.File;

/**
 * Единственный источник правды о путях P3WIN.
 *
 * <h3>Почему так</h3>
 * В ядре Winlator путь {@code /data/data/com.winlator} зашит в rootfs, в ld.so.cache,
 * в ICD-описания Vulkan и в JNI-макросы. Требование проекта — «никаких хардкод-путей
 * в новом коде»: любая попытка писать абсолютные строки в P3-слое закончится тем,
 * что после смены applicationId приложение установится, но контейнеры перестанут
 * запускаться.
 *
 * <p>Поэтому:
 * <ul>
 *   <li>все каталоги P3 выводятся из {@link Context#getFilesDir()} / {@link Context#getCacheDir()};</li>
 *   <li>каталоги <b>ядра</b> (rootfs, usr, home) не дублируются строкой, а берутся
 *       из {@code RootFS}/{@code ContainerManager} — там же, где их видит ядро;</li>
 *   <li>{@link #checkNativePrefix(String)} сверяет фактические пути с теми,
 *       на которые рассчитаны бинарные артефакты, и пишет понятную ошибку в лог.</li>
 * </ul>
 *
 * <p>Схема каталогов:
 * <pre>
 * files/
 *   ├── rootfs/                      ← ядро (Wine, Box64, XServer)
 *   ├── installed_components/        ← ядро (turnip, dxvk, vkd3d...)
 *   ├── profiles/                    ← ядро (.icp раскладки)
 *   └── p3win/                       ← P3WIN, всё новое только здесь
 *        ├── logs/
 *        ├── backups/
 *        ├── library/                ← обложки/постеры игр
 *        ├── graphics_drivers/       ← слоты драйверов и бэкапы
 *        └── imports/                ← временные .p3c архивы
 * </pre>
 */
public final class AppPaths {

    /** Имя подкаталога P3WIN внутри filesDir. */
    public static final String P3_DIR = "p3win";

    private final File filesDir;
    private final File cacheDir;
    private final File dataDir;
    private final String packageName;

    private AppPaths(Context context) {
        this.filesDir = context.getFilesDir();
        this.cacheDir = context.getCacheDir();
        this.dataDir = filesDir != null ? filesDir.getParentFile() : null;
        this.packageName = context.getPackageName();
    }

    public static AppPaths of(Context context) {
        Context app = context.getApplicationContext();
        return new AppPaths(app != null ? app : context);
    }

    // ------------------------------------------------------------------
    // Корневые каталоги приложения
    // ------------------------------------------------------------------

    public File getFilesDir() {
        return filesDir;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public File getDataDir() {
        return dataDir;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getDataDirPath() {
        return dataDir != null ? dataDir.getAbsolutePath() : "";
    }

    public String getCacheDirPath() {
        return cacheDir != null ? cacheDir.getAbsolutePath() : "";
    }

    // ------------------------------------------------------------------
    // Каталоги ядра (не создаём — их создаёт ядро)
    // ------------------------------------------------------------------

    public File getRootFSDir() {
        return new File(filesDir, "rootfs");
    }

    public File getRootFSLibDir() {
        return new File(getRootFSDir(), "usr/lib");
    }

    public File getRootFSTmpDir() {
        return new File(getRootFSDir(), "tmp");
    }

    public File getRootFSHomeDir() {
        return new File(getRootFSDir(), "home");
    }

    public File getInstalledComponentsDir() {
        return new File(filesDir, "installed_components");
    }

    public File getComponentDir(String componentName) {
        return new File(getInstalledComponentsDir(), componentName);
    }

    public File getInputProfilesDir() {
        // Ядро хранит .icp именно здесь (InputControlsManager.getProfilesDir).
        return new File(filesDir, "profiles");
    }

    // ------------------------------------------------------------------
    // Каталоги P3WIN
    // ------------------------------------------------------------------

    private File p3Root() {
        return new File(filesDir, P3_DIR);
    }

    public File getLogsDir() {
        return ensure(new File(p3Root(), "logs"));
    }

    public File getBackupsDir() {
        return ensure(new File(p3Root(), "backups"));
    }

    public File getLibraryDir() {
        return ensure(new File(p3Root(), "library"));
    }

    public File getGameArtworkDir(long gameId) {
        return ensure(new File(getLibraryDir(), String.valueOf(gameId)));
    }

    public File getGraphicsDriversDir() {
        return ensure(new File(p3Root(), "graphics_drivers"));
    }

    /** Слот активного набора драйверов (то, что подставлено в rootfs сейчас). */
    public File getActiveGraphicsDriverSlot() {
        return new File(getGraphicsDriversDir(), "active");
    }

    /** Резервная копия предыдущего драйвера — для безопасного отката. */
    public File getGraphicsDriverBackupDir() {
        return ensure(new File(getGraphicsDriversDir(), "backup"));
    }

    /** Временный каталог для импорта архивов контейнеров. */
    public File getImportsDir() {
        return ensure(new File(p3Root(), "imports"));
    }

    /** Каталог обмена: используется ядром как «Download» внутри Wine. */
    public File getSharedDir() {
        return ensure(new File(filesDir, "storage"));
    }

    private static File ensure(File dir) {
        if (dir != null && !dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    // ------------------------------------------------------------------
    // Диагностика
    // ------------------------------------------------------------------

    /**
     * Сверить фактический data-dir с тем, на который рассчитаны артефакты ядра.
     *
     * <p>Вызывается один раз на старте. Задача — превратить «тихий» отказ
     * (контейнер не стартует через три минуты после распаковки) в явную
     * запись в логе: «пути разъехались, запустите scripts/rename_package.ps1».
     *
     * @param expectedDataDir эталонный префикс, например {@code /data/data/<package>}
     * @return null, если всё совпадает; иначе текст проблемы
     */
    public String checkNativePrefix(String expectedDataDir) {
        if (expectedDataDir == null || expectedDataDir.isEmpty()) return null;
        String actual = getDataDirPath();
        if (actual.isEmpty()) return "Не удалось определить каталог данных приложения";
        if (!actual.equals(expectedDataDir)) {
            return "Рассинхронизация путей: приложение работает в " + actual
                    + ", а артефакты ядра (rootfs/JNI) рассчитаны на " + expectedDataDir
                    + ". Запустите scripts/rename_package.ps1 перед сборкой.";
        }
        return null;
    }
}
