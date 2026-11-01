package com.p3win.core.integration;

import android.content.Context;

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
     * Создать контейнер через ядро и дождаться результата.
     * <p>Ядро само распаковывает pattern контейнера и wineprefix, поэтому
     * операция занимает десятки секунд — только фоновый поток.
     */
    public com.winlator.container.Container create(JSONObject creationData) {
        final com.winlator.container.Container[] holder = new com.winlator.container.Container[1];
        final Object lock = new Object();

        coreManager.createContainerAsync(creationData, container -> {
            synchronized (lock) {
                holder[0] = container;
                lock.notifyAll();
            }
        });

        synchronized (lock) {
            try {
                lock.wait(CREATE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

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

        synchronized (lock) {
            try {
                lock.wait(CREATE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (done[0]) sync();
        return done[0];
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

        synchronized (lock) {
            try {
                lock.wait(CREATE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (done[0]) {
            P3Database database = P3Database.get(context);
            if (database != null) database.containerDao().delete(core.id);
        }
        return done[0];
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
