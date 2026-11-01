package com.p3win.core.integration;

import android.content.Context;
import android.os.Looper;

import com.p3win.core.log.P3Log;
import com.p3win.core.profile.ContainerProfile;
import com.p3win.core.profile.ContainerProfileApplier;
import com.p3win.data.P3Database;
import com.p3win.data.dao.ContainerMetaDao;
import com.p3win.data.entity.ContainerMeta;
import com.winlator.container.ContainerManager;
import com.winlator.xenvironment.RootFS;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Мост между контейнерами ядра и метаданными P3WIN.
 *
 * <h3>Разделение источников правды</h3>
 * <ul>
 *   <li><b>Настройки контейнера</b> (разрешение, драйверы, Box64, wineprefix) —
 *       всегда ядро, JSON внутри {@code files/rootfs/home/xuser-<id>};</li>
 *   <li><b>Метаданные P3</b> (профиль, избранное, статистика, заметки) — наша база;</li>
 *   <li>этот класс сводит их и досоздаёт недостающие записи, поэтому контейнеры,
 *       созданные в «родном» экране Winlator, видны в P3 Shell.</li>
 * </ul>
 *
 * <p>Все методы блокирующие — вызывать из фонового потока
 * ({@link com.p3win.data.TaskRunner}).
 */
public final class CoreContainerLink {

    private static final String TAG = "ContainerLink";
    private static final long CREATE_TIMEOUT_MS = 180_000L;

    private final Context context;
    private final ContainerManager coreManager;

    public CoreContainerLink(Context context) {
        this.context = context.getApplicationContext();
        this.coreManager = new ContainerManager(this.context);
    }

    public ContainerManager core() {
        return coreManager;
    }

    public List<com.winlator.container.Container> coreContainers() {
        return coreManager.getContainers();
    }

    public com.winlator.container.Container coreContainer(long id) {
        return coreManager.getContainerById((int) id);
    }

    // ------------------------------------------------------------------
    // Синхронизация
    // ------------------------------------------------------------------

    /**
     * Синхронизировать метаданные P3 с контейнерами ядра.
     *
     * @return актуальный список метаданных, соответствующий ядру
     */
    public List<ContainerMeta> sync() {
        P3Database database = P3Database.get(context);
        if (database == null) return new ArrayList<>();

        ContainerMetaDao dao = database.containerDao();
        List<ContainerMeta> existing = dao.findAll();
        Set<Long> coreIds = new HashSet<>();

        for (com.winlator.container.Container core : coreManager.getContainers()) {
            coreIds.add((long) core.id);

            ContainerMeta meta = null;
            for (ContainerMeta candidate : existing) {
                if (candidate.id == core.id) {
                    meta = candidate;
                    break;
                }
            }

            if (meta == null) {
                ContainerMeta created = new ContainerMeta();
                created.id = core.id;
                created.name = core.getName();
                created.box64Preset = core.getBox64Preset();
                created.wineVersion = core.getWineVersion();
                created.graphicsDriver = core.getGraphicsDriver();
                created.createdAt = System.currentTimeMillis();
                created.sizeBytes = directorySize(core.getRootDir());
                dao.upsert(created);
                P3Log.info(TAG, "Новый контейнер ядра: #" + core.id + " «" + core.getName() + "»");
            } else if (!meta.name.equals(core.getName())) {
                // Имя меняли в родном экране — подтягиваем, чтобы списки не расходились.
                meta.name = core.getName();
                meta.box64Preset = core.getBox64Preset();
                meta.graphicsDriver = core.getGraphicsDriver();
                dao.upsert(meta);
            }
        }

        for (ContainerMeta meta : existing) {
            if (!coreIds.contains(meta.id)) {
                dao.delete(meta.id);
                P3Log.info(TAG, "Удалён устаревший контейнер P3 #" + meta.id);
            }
        }

        return dao.findAll();
    }

    // ------------------------------------------------------------------
    // Создание / удаление
    // ------------------------------------------------------------------

    /**
     * Подождать асинхронный колбэк ядра, не рискуя заморозить интерфейс.
     *
     * <h3>В чём была проблема</h3>
     * {@code ContainerManager.createContainerAsync} и его братья вызывают
     * колбэк через {@code Handler.post} на <b>главный</b> лупер. Если ждать
     * их через {@code Object.wait()} в UI-потоке, колбэк никогда не выполнится:
     * главный поток стоит в ожидании самого себя — классический дедлок,
     * который на устройстве выглядит как «приложение зависло».
     *
     * <p>Решение: перед ожиданием проверяем поток. На главном потоке не ждём
     * вовсе и возвращаем false — вызывающая сторона обязана работать в фоне
     * ({@code P3Fragment.async} / {@code TaskRunner}). Это честнее таймаута:
     * таймаут вернул бы «не удалось» через 60 секунд, повесив интерфейс.
     *
     * @param lock   объект-монитор
     * @param done   флаг завершения (читается под тем же монитором)
     * @param caller описание операции для журнала
     */
    private boolean awaitCore(Object lock, boolean[] done, String caller) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            P3Log.error(TAG, caller + ": вызов из UI-потока заблокировал бы колбэк ядра. "
                    + "Операцию нужно выполнять в фоновом потоке.");
            return false;
        }

        long deadline = System.currentTimeMillis() + CREATE_TIMEOUT_MS;
        synchronized (lock) {
            while (!done[0]) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    P3Log.warn(TAG, caller + ": ядро не ответило за " + CREATE_TIMEOUT_MS + " мс");
                    return false;
                }
                try {
                    lock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Создать контейнер через ядро и дождаться результата.
     * <p>Ядро само распаковывает pattern контейнера и wineprefix, поэтому
     * операция занимает десятки секунд — только фоновый поток.
     */
    public com.winlator.container.Container create(JSONObject creationData) {
        final com.winlator.container.Container[] holder = new com.winlator.container.Container[1];
        final Object lock = new Object();

        final boolean[] finished = {false};
        coreManager.createContainerAsync(creationData, container -> {
            synchronized (lock) {
                holder[0] = container;
                finished[0] = true;
                lock.notifyAll();
            }
        });

        // Ожидание с флагом, а не «проснулись — значит готово»: колбэк ядра
        // может прийти null при ошибке, и тогда старый код просто ждал таймаут.
        if (!awaitCore(lock, finished, "create")) return null;

        if (holder[0] != null) {
            P3Database database = P3Database.get(context);
            if (database != null) {
                ContainerMeta meta = new ContainerMeta();
                meta.id = holder[0].id;
                meta.name = holder[0].getName();
                meta.box64Preset = holder[0].getBox64Preset();
                meta.wineVersion = holder[0].getWineVersion();
                meta.graphicsDriver = holder[0].getGraphicsDriver();
                meta.createdAt = System.currentTimeMillis();
                database.containerDao().upsert(meta);
            }
            com.p3win.core.session.MilestoneEngine.refresh(context);
            P3Log.info(TAG, "Создан контейнер #" + holder[0].id + " «" + holder[0].getName() + "»");
        }
        return holder[0];
    }

    /** Дублировать контейнер (ядро копирует каталог и настройки). */
    public boolean duplicate(com.winlator.container.Container core) {
        if (core == null) return false;
        final boolean[] done = {false};
        final Object lock = new Object();

        coreManager.duplicateContainerAsync(core, () -> {
            synchronized (lock) {
                done[0] = true;
                lock.notifyAll();
            }
        });

        if (!awaitCore(lock, done, "duplicate")) return false;
        sync();
        return true;
    }

    /** Удалить контейнер (ядро чистит каталог в rootfs). */
    public boolean remove(com.winlator.container.Container core) {
        if (core == null) return false;
        final boolean[] done = {false};
        final Object lock = new Object();

        coreManager.removeContainerAsync(core, () -> {
            synchronized (lock) {
                done[0] = true;
                lock.notifyAll();
            }
        });

        if (!awaitCore(lock, done, "remove")) return false;
        P3Database database = P3Database.get(context);
        if (database != null) database.containerDao().delete(core.id);
        return true;
    }

    // ------------------------------------------------------------------
    // Профили и размер
    // ------------------------------------------------------------------

    /** Применить профиль P3 к контейнеру и сохранить изменения в ядре. */
    public boolean applyProfile(long containerId, ContainerProfile profile) {
        com.winlator.container.Container core = coreContainer(containerId);
        if (core == null || profile == null) return false;

        ContainerProfileApplier.apply(core, profile);
        core.saveData();

        P3Database database = P3Database.get(context);
        if (database != null) {
            ContainerMeta meta = database.containerDao().findById(containerId);
            if (meta != null) {
                meta.profileId = profile.id;
                meta.box64Preset = core.getBox64Preset();
                meta.graphicsDriver = core.getGraphicsDriver();
                database.containerDao().upsert(meta);
            }
        }
        P3Log.info(TAG, "Профиль «" + profile.title + "» применён к контейнеру #" + containerId);
        return true;
    }

    /** Пересчитать и закэшировать размер контейнера на диске. */
    public long refreshSize(long containerId) {
        com.winlator.container.Container core = coreContainer(containerId);
        if (core == null) return 0;
        long size = directorySize(core.getRootDir());
        P3Database database = P3Database.get(context);
        if (database != null) database.containerDao().setSize(containerId, size);
        return size;
    }

    /** Свободное место в разделе, где живут контейнеры. */
    public long freeSpace() {
        File root = RootFS.find(context).getRootDir();
        return root != null ? root.getUsableSpace() : 0L;
    }

    /**
     * Готова ли система к запуску контейнеров.
     *
     * <p>Проверяется именно rootfs (Wine + Box64 + XServer), а не «установлено
     * приложение»: до первого запуска {@code RootFSInstaller} контейнеров нет,
     * а экран Shell должен показать «настроить окружение», а не пустой список
     * с непонятной ошибкой.
     */
    public boolean isSystemReady() {
        try {
            RootFS rootFS = RootFS.find(context);
            return rootFS.isValid()
                    && rootFS.getVersion() >= com.winlator.xenvironment.RootFSInstaller.LATEST_VERSION;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Открыть штатный установщик rootfs (экран ядра сам вызовет installIfNeeded). */
    public void openSystemInstaller(android.app.Activity activity) {
        if (activity == null) return;
        activity.startActivity(new android.content.Intent(activity, com.winlator.MainActivity.class));
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    public static long directorySize(File file) {
        if (file == null) return 0;
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        if (children == null) return 0;
        long sum = 0;
        for (File child : children) sum += directorySize(child);
        return sum;
    }
}
