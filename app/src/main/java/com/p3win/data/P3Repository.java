package com.p3win.data;

import android.content.Context;

import com.p3win.data.dao.ContainerMetaDao;
import com.p3win.data.dao.GameDao;
import com.p3win.data.dao.GameProfileDao;
import com.p3win.data.dao.GraphicsDriverDao;
import com.p3win.data.dao.InputLayoutDao;
import com.p3win.data.dao.MilestoneDao;
import com.p3win.data.dao.SessionDao;

import java.io.File;

/**
 * Фасад слоя данных P3WIN.
 *
 * <p>UI не работает с {@link android.database.sqlite.SQLiteDatabase} напрямую
 * и не выполняет запросы в main-thread: всё идёт через репозиторий и
 * {@link TaskRunner}.
 */
public class P3Repository {

    private static P3Repository instance;

    private final Context context;
    private final P3Database database;

    public P3Repository(Context context) {
        this.context = context.getApplicationContext();
        this.database = P3Database.get(this.context);
    }

    public static synchronized P3Repository get(Context context) {
        if (instance == null) instance = new P3Repository(context);
        return instance;
    }

    public void open() {
        database.open();
    }

    public Context context() {
        return context;
    }

    public ContainerMetaDao containers() {
        return database.containerDao();
    }

    public GameDao games() {
        return database.gameDao();
    }

    public GameProfileDao profiles() {
        return database.gameProfileDao();
    }

    public GraphicsDriverDao drivers() {
        return database.graphicsDriverDao();
    }

    public SessionDao sessions() {
        return database.sessionDao();
    }

    public InputLayoutDao layouts() {
        return database.inputLayoutDao();
    }

    public MilestoneDao milestones() {
        return database.milestoneDao();
    }

    /** Каталог обложек игры; создаётся при первом обращении. */
    public File artworkDir(long gameId) {
        File dir = new File(com.p3win.core.paths.AppPaths.of(context).getLibraryDir(),
                String.valueOf(gameId));
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }
}
