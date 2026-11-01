package com.p3win.core.integration;

import android.app.Activity;
import android.content.Context;

import com.p3win.core.log.P3Log;
import com.p3win.core.paths.AppPaths;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.TarCompressorUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Установка Wine-образа (rootfs) из оболочки P3.
 *
 * <h3>Почему не «открыть экран ядра»</h3>
 * Штатный {@link RootFSInstaller#install(com.winlator.MainActivity)} требует
 * именно {@code MainActivity}: он показывает её диалог прогресса, сбрасывает
 * свою версию настроек и вызывает {@code AppUtils.keepScreenOn}. Запускать
 * это из P3 Shell нельзя — пришлось бы тащить пользователя в чужой интерфейс
 * ровно на том шаге, где он впервые открыл приложение.
 *
 * <h3>Что здесь повторяется, а что нет</h3>
 * Повторяются только три шага распаковки: очистка каталога, извлечение
 * {@code rootfs.tzst} из APK и запись файла версии. Вся содержательная
 * логика — формат архива, имя ассета, номер актуальной версии — берётся у
 * ядра ({@link RootFS}, {@link RootFSInstaller#LATEST_VERSION}). Если Winlator
 * сменит версию rootfs, P3 подхватит её автоматически.
 *
 * <p>Отдельно повторяется сброс {@code rfsVersion} у контейнеров: ядро делает
 * это в приватном методе, а без него после переустановки образа старые
 * контейнеры считают себя актуальными и wineprefix не обновляется.
 */
public final class RootFSBridge {

    private static final String TAG = "RootFS";
    private static final String ASSET = "rootfs.tzst";

    /** Колбэк прогресса: 0..100 и текст для UI. */
    public interface Listener {
        void onProgress(int percent);
        void onFinished(boolean success, String error);
    }

    private RootFSBridge() {
    }

    /** Нужна ли установка (нет образа или он старее актуального). */
    public static boolean needsInstall(Context context) {
        try {
            RootFS rootFS = RootFS.find(context);
            return !rootFS.isValid()
                    || rootFS.getVersion() < RootFSInstaller.LATEST_VERSION;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * Распаковать rootfs.
     *
     * <p>Блокирующий вызов: запускать только в фоновом потоке
     * ({@code TaskRunner} / {@code P3Fragment.asyncVoid}). Прогресс приходит
     * на потоке распаковки — экран должен сам переключиться в main.
     *
     * @return null при успехе, иначе текст ошибки
     */
    public static String install(Context context, Listener listener) {
        if (context == null) return "Нет контекста";

        final Context app = context.getApplicationContext();
        final RootFS rootFS = RootFS.find(app);
        final File rootDir = rootFS.getRootDir();

        try {
            long started = System.currentTimeMillis();
            P3Log.info(TAG, "Распаковка Wine-образа в " + rootDir.getAbsolutePath());

            clearRootDir(rootDir);

            // Размер распакованного образа берём из длины архива, а не через
            // TarCompressorUtils.getContentLength: тот честно распаковывает
            // весь архив во временный каталог только для подсчёта, то есть
            // удваивает время установки (сотни мегабайт). Точность процента
            // здесь не важнее скорости — оценка по сжатому размеру с
            // коэффициентом даёт достаточный для прогресс-бара диапазон.
            long estimate = estimateUnpackedSize(app);
            AtomicLong total = new AtomicLong();

            boolean success = TarCompressorUtils.extract(
                    TarCompressorUtils.Type.ZSTD, app, ASSET, rootDir, (file, size) -> {
                        if (size > 0 && estimate > 0) {
                            long done = total.addAndGet(size);
                            int percent = (int) Math.min(99, (done * 100) / estimate);
                            if (listener != null) listener.onProgress(percent);
                        }
                        return file;
                    });

            if (!success) {
                P3Log.error(TAG, "Распаковка не удалась: " + ASSET + " повреждён или отсутствует");
                return "Не удалось распаковать Wine-образ. Проверьте целостность APK "
                        + "(scripts/verify_assets.sh).";
            }

            rootFS.createRFSVersionFile(RootFSInstaller.LATEST_VERSION);
            resetComponentVersions(app);
            resetContainerVersions(app);

            long seconds = (System.currentTimeMillis() - started) / 1000;
            P3Log.info(TAG, "Wine-образ установлен за " + seconds + " с, версия "
                    + RootFSInstaller.LATEST_VERSION);
            if (listener != null) listener.onProgress(100);
            return null;
        } catch (Throwable t) {
            P3Log.error(TAG, "Установка Wine-образа завершилась ошибкой", t);
            return "Ошибка установки: " + t.getClass().getSimpleName();
        }
    }

    /**
     * Оценка размера распакованного образа.
     *
     * <p>zstd сжимает rootfs примерно втрое. Точности здесь не нужно: значение
     * используется только как знаменатель прогресс-бара, а он упирается в 99%
     * до момента записи файла версии. Если оценка ошибочна, бар просто идёт
     * быстрее или медленнее — на корректность установки это не влияет.
     */
    private static long estimateUnpackedSize(Context context) {
        try (java.io.InputStream stream = context.getAssets().open(ASSET)) {
            long packed = stream.available();
            return packed > 0 ? packed * 3 : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Удалить каталог образа, сохранив пользовательские данные.
     *
     * <p>Логика совпадает с ядром: {@code home} и {@code opt} — это данные
     * контейнеров (wineprefix), их нельзя стирать при обновлении образа.
     * {@code opt/installed-wine} тоже сохраняется — там лежат установленные
     * версии Wine.
     */
    private static void clearRootDir(File rootDir) {
        if (!rootDir.isDirectory()) return;
        File[] files = rootDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String name = file.getName();
                if (name.equals("home")) continue;
                if (name.equals("opt")) {
                    clearOptDir(file);
                    continue;
                }
            }
            com.winlator.core.FileUtils.delete(file);
        }
    }

    private static void clearOptDir(File optDir) {
        File[] files = optDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().equals("installed-wine")) continue;
            com.winlator.core.FileUtils.delete(file);
        }
    }

    /**
     * Сбросить отметки уже распакованных компонентов.
     *
     * <p>Ядро делает это в {@code SettingsFragment.resetPreferenceVersions} —
     * методе, который вызывается только из его же установщика. Без сброса
     * после переустановки образа остаются «уже распакованы» для Box64 и
     * графического драйвера, а их файлов в новом rootfs ещё нет: игра
     * запускается и падает на отсутствующем {@code box64}.
     *
     * <p>Ключи повторяют ядро дословно: это часть его контракта с настройками,
     * а не наши данные.
     */
    private static void resetComponentVersions(Context context) {
        try {
            android.content.SharedPreferences preferences =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
            preferences.edit()
                    .putString("box64_version", com.winlator.core.DefaultVersion.BOX64)
                    .remove("current_box64_version")
                    .remove("current_graphics_driver")
                    .apply();
            P3Log.info(TAG, "Отметки компонентов сброшены — распакуются заново");
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось сбросить отметки компонентов", t);
        }
    }

    /**
     * Сбросить у контейнеров отметку версии образа.
     *
     * <p>Без этого после обновления rootfs контейнеры продолжают считать, что
     * у них актуальный wineprefix, и не пересоздают его — игры падают на
     * несовместимых библиотеках. Условие такое же, как у ядра: контейнер
     * использует основную версию Wine и его префикс ещё не новее
     * {@code UPDATE_WINEPREFIX_VERSION}.
     */
    private static void resetContainerVersions(Context context) {
        try {
            ContainerManager manager = new ContainerManager(context);
            for (Container container : manager.getContainers()) {
                String rfsVersion = container.getExtra("rfsVersion");
                container.putExtra("rfsVersion", null);
                if (rfsVersion.isEmpty()) {
                    container.saveData();
                    continue;
                }
                String wineVersion = container.getWineVersion();
                short version;
                try {
                    version = Short.parseShort(rfsVersion);
                } catch (NumberFormatException e) {
                    version = Short.MAX_VALUE;
                }
                if (com.winlator.core.WineInfo.isMainWineVersion(wineVersion)
                        && version <= RootFSInstaller.UPDATE_WINEPREFIX_VERSION) {
                    container.putExtra("wineprefixNeedsUpdate", "t");
                }
                container.saveData();
            }
            P3Log.info(TAG, "Отметки версии образа у контейнеров сброшены");
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось сбросить версии контейнеров", t);
        }
    }

    /** Свободное место в каталоге данных — для предупреждения перед установкой. */
    public static long freeSpace(Context context) {
        try {
            return AppPaths.of(context).getFilesDir().getUsableSpace();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Нужно ли просить разрешение на запись перед установкой (API ≤ 28). */
    public static boolean needsStoragePermission(Activity activity) {
        if (activity == null) return false;
        if (android.os.Build.VERSION.SDK_INT > 28) return false;
        return activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
