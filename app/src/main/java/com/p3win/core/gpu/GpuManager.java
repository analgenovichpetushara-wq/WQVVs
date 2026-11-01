package com.p3win.core.gpu;

import android.content.Context;

import com.p3win.core.log.P3Log;
import com.p3win.core.paths.AppPaths;
import com.p3win.data.P3Database;
import com.p3win.data.dao.GraphicsDriverDao;
import com.p3win.data.entity.GraphicsDriver;
import com.winlator.container.Container;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;

import java.io.File;
import java.util.List;

/**
 * P3 GPU Manager — установка, активация и откат GPU-драйверов.
 *
 * <h3>Модель безопасности</h3>
 * Драйвер на этом устройстве — самое рискованное, что можно поменять: неудачная
 * версия Turnip даёт чёрный экран в игре и «приложение просто закрылось».
 * Поэтому любая активация двухшаговая:
 * <ol>
 *   <li><b>снимок</b> текущего состояния в {@code files/p3win/graphics_drivers/backup}
 *       (конфиги контейнеров + какие версии стояли);</li>
 *   <li><b>применение</b> новой версии к контейнеру через поле
 *       {@code graphicsDriverConfig} ядра.</li>
 * </ol>
 * Значит откат — это всегда «вернуть снимок», а не «угадать, что было».
 *
 * <h3>Что считается «установленным драйвером»</h3>
 * У ядра нет отдельного «активного драйвера системы»: каждый контейнер
 * хранит свой {@code graphicsDriver} + {@code graphicsDriverConfig}.
 * P3 GPU Manager работает поверх этого: хранит глобальный выбор и умеет
 * применять его ко всем или к выбранному контейнеру. Так модель остаётся
 * совместимой с ядром и не ломает контейнеры, созданные в «родном» экране.
 */
public final class GpuManager {

    private static final String TAG = "GpuManager";
    private static final String SNAPSHOT_FILE = "graphics-snapshot.txt";

    private final Context context;

    public GpuManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ------------------------------------------------------------------
    // Каталог
    // ------------------------------------------------------------------

    public List<GraphicsDriver> catalog() {
        List<GraphicsDriver> drivers = DriverCatalog.list(context);
        // Отмечаем установленные/активные по базе P3, чтобы UI показывал состояние.
        P3Database database = P3Database.get(context);
        if (database != null) {
            GraphicsDriverDao dao = database.graphicsDriverDao();
            for (GraphicsDriver driver : drivers) {
                GraphicsDriver stored = dao.find(driver.driverId, driver.version);
                if (stored != null) {
                    driver.installed = stored.installed;
                    driver.active = stored.active;
                    driver.verifiedOnReference = stored.verifiedOnReference;
                }
            }
        }
        return drivers;
    }

    /** Записать каталог в базу (вызывается при открытии экрана GPU). */
    public void syncCatalog() {
        P3Database database = P3Database.get(context);
        if (database == null) return;
        GraphicsDriverDao dao = database.graphicsDriverDao();
        for (GraphicsDriver driver : DriverCatalog.list(context)) {
            GraphicsDriver existing = dao.find(driver.driverId, driver.version);
            if (existing != null) {
                driver.installed = existing.installed;
                driver.active = existing.active;
                driver.verifiedOnReference = existing.verifiedOnReference;
                driver.installedAt = existing.installedAt;
            }
            dao.upsert(driver);
        }
    }

    // ------------------------------------------------------------------
    // Установка / активация
    // ------------------------------------------------------------------

    /**
     * «Установить» драйвер = зафиксировать его как доступный в базе P3.
     *
     * <p>Физически архивы Turnip уже лежат в APK (assets) и распаковываются
     * ядром при первом использовании — дублировать эту логику нельзя.
     * Поэтому установка здесь означает: проверить, что архив реально есть,
     * и запомнить факт готовности.
     *
     * @return null при успехе, иначе текст ошибки
     */
    public String install(GraphicsDriver driver) {
        if (driver == null) return "Драйвер не выбран";

        File archive = DriverCatalog.archiveFile(context, driver);
        boolean builtin = GeneralComponents.isBuiltinComponent(
                GeneralComponents.Type.TURNIP, driver.version);

        if (!builtin && (archive == null || !archive.isFile())) {
            return "Архив драйвера не найден: " + driver.displayName
                    + ". Установите его в настройках ядра.";
        }

        P3Database database = P3Database.get(context);
        if (database != null) {
            GraphicsDriverDao dao = database.graphicsDriverDao();
            GraphDriverFlags.flags(driver, true, false);
            dao.upsert(driver);
        }
        P3Log.info(TAG, "Драйвер доступен: " + driver.displayName);
        return null;
    }

    /**
     * Активировать драйвер: снимок → применение к контейнеру → запись состояния.
     *
     * @param driver        драйвер
     * @param containerCore контейнер ядра (может быть null — тогда только
     *                      глобальная отметка, без изменения контейнеров)
     * @return null при успехе, иначе текст ошибки
     */
    public String activate(GraphicsDriver driver, com.winlator.container.Container containerCore) {
        if (driver == null) return "Драйвер не выбран";

        String warning = DriverCatalog.compatibilityWarning(driver, GpuCapability.detect(context));
        if (warning != null && !warning.isEmpty()) {
            P3Log.warn(TAG, "Совместимость: " + warning);
        }

        try {
            // 1. Снимок текущего состояния — до любых изменений.
            if (containerCore != null) snapshot(containerCore);

            // 2. Применение к контейнеру.
            if (containerCore != null) {
                applyToContainer(containerCore, driver);
            }

            // 3. Отметка в базе.
            P3Database database = P3Database.get(context);
            if (database != null) {
                GraphicsDriverDao dao = database.graphicsDriverDao();
                GraphDriverFlags.flags(driver, true, true);
                dao.upsert(driver);
                dao.setActive(driver.driverId, driver.version);
            }

            P3Log.info(TAG, "Активирован драйвер " + driver.displayName
                    + (containerCore != null ? " для контейнера #" + containerCore.id : ""));
            return null;
        } catch (Throwable t) {
            P3Log.error(TAG, "Активация драйвера не удалась", t);
            return "Ошибка активации: " + t.getClass().getSimpleName();
        }
    }

    /**
     * Применить драйвер к контейнеру.
     *
     * <p>Формат ядра: {@code graphicsDriver} = «Vulkan,OpenGL» через запятую,
     * {@code graphicsDriverConfig} = два KeyValueSet через {@code |}.
     * OpenGL-часть и её конфиг сохраняем — P3 меняет только Vulkan-сторону.
     */
    public void applyToContainer(Container container, GraphicsDriver driver) {
        if (container == null || driver == null) return;

        String openGl = GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
        String current = container.getGraphicsDriver();
        if (current != null) {
            String[] parts = GraphicsDrivers.parseIdentifiers(current);
            if (parts.length > 1 && GraphicsDrivers.isOpenGLDriver(parts[1])) openGl = parts[1];
        }

        if (GraphicsDrivers.isVulkanDriver(driver.driverId)) {
            container.setGraphicsDriver(driver.driverId + "," + openGl);
            com.winlator.core.KeyValueSet[] configs = GraphicsDrivers.parseConfigs(
                    container.getGraphicsDriver(), container.getGraphicsDriverConfig());
            com.winlator.core.KeyValueSet vulkanConfig = configs[0];
            vulkanConfig.put("version", driver.version);
            container.setGraphicsDriverConfig(vulkanConfig.toString() + "|" + configs[1].toString());
        } else if (GraphicsDrivers.isOpenGLDriver(driver.driverId)) {
            container.setGraphicsDriver(GraphicsDrivers.DEFAULT_VULKAN_DRIVER + "," + driver.driverId);
        }

        container.saveData();
    }

    // ------------------------------------------------------------------
    // Снимок и откат
    // ------------------------------------------------------------------

    /** Сохранить текущее состояние контейнера перед изменениями. */
    public boolean snapshot(Container container) {
        if (container == null) return false;
        try {
            File dir = AppPaths.of(context).getGraphicsDriverBackupDir();
            if (!dir.isDirectory() && !dir.mkdirs()) return false;

            String content = "containerId=" + container.id + "\n"
                    + "graphicsDriver=" + container.getGraphicsDriver() + "\n"
                    + "graphicsDriverConfig=" + container.getGraphicsDriverConfig() + "\n"
                    + "dxwrapper=" + container.getDXWrapper() + "\n"
                    + "dxwrapperConfig=" + container.getDXWrapperConfig() + "\n"
                    + "time=" + System.currentTimeMillis() + "\n";

            FileUtils.writeString(new File(dir, SNAPSHOT_FILE), content);
            P3Log.info(TAG, "Снимок GPU-настроек контейнера #" + container.id + " сохранён");
            return true;
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось сохранить снимок GPU-настроек", t);
            return false;
        }
    }

    /**
     * Откатить драйвер контейнера к последнему снимку.
     *
     * @return null при успехе, иначе текст ошибки
     */
    public String rollback(Container container) {
        if (container == null) return "Контейнер не найден";

        File file = new File(AppPaths.of(context).getGraphicsDriverBackupDir(), SNAPSHOT_FILE);
        if (!file.isFile()) return "Резервной копии нет — откатывать нечего";

        try {
            String graphicsDriver = null;
            String graphicsDriverConfig = null;
            for (String line : FileUtils.readLines(file, true)) {
                int separator = line.indexOf('=');
                if (separator <= 0) continue;
                String key = line.substring(0, separator);
                String value = line.substring(separator + 1);
                if (key.equals("graphicsDriver")) graphicsDriver = value;
                else if (key.equals("graphicsDriverConfig")) graphicsDriverConfig = value;
            }

            if (graphicsDriver != null && !graphicsDriver.isEmpty()) {
                container.setGraphicsDriver(graphicsDriver);
            }
            if (graphicsDriverConfig != null) {
                container.setGraphicsDriverConfig(graphicsDriverConfig);
            }
            container.saveData();

            P3Database database = P3Database.get(context);
            if (database != null) database.graphicsDriverDao().clearActive();

            P3Log.info(TAG, "Выполнен откат GPU-настроек контейнера #" + container.id);
            return null;
        } catch (Throwable t) {
            P3Log.error(TAG, "Откат не удался", t);
            return "Ошибка отката: " + t.getClass().getSimpleName();
        }
    }

    /** Есть ли снимок для отката. */
    public boolean hasSnapshot() {
        return new File(AppPaths.of(context).getGraphicsDriverBackupDir(), SNAPSHOT_FILE).isFile();
    }

    /** Утилита: выставить флаги записи драйвера без отдельного конструктора. */
    private static final class GraphDriverFlags {
        static void flags(GraphicsDriver driver, boolean installed, boolean active) {
            driver.installed = installed;
            if (active) driver.active = true;
        }
    }
}
