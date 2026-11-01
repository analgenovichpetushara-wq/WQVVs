package com.p3win.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.p3win.data.dao.ContainerMetaDao;
import com.p3win.data.dao.GameDao;
import com.p3win.data.dao.GameProfileDao;
import com.p3win.data.dao.GraphicsDriverDao;
import com.p3win.data.dao.InputLayoutDao;
import com.p3win.data.dao.MilestoneDao;
import com.p3win.data.dao.SessionDao;

/**
 * SQLite-база P3WIN.
 *
 * <h3>Почему не Room</h3>
 * Room — правильный выбор для приложения на «чистом» Android, но здесь рядом
 * работает нативный слой (Wine/Box64) и включён R8 в release. Каждая
 * аннотация с генерацией кода добавляет риск тихого отказа уже внутри
 * контейнера, где отладка стоит дорого. {@link SQLiteOpenHelper} даёт
 * предсказуемость: ноль сгенерированного кода, схема целиком в одном месте.
 *
 * <h3>Границы с ядром</h3>
 * База P3WIN — отдельный файл {@code p3win.db}. Ядро продолжает жить на своих
 * JSON/desktop-файлах и остаётся источником правды о настройках контейнеров.
 * Здесь — только оболочка: библиотека, профили P3, раскладки, статистика,
 * состояние GPU-менеджера.
 */
public class P3Database extends SQLiteOpenHelper {

    public static final String DB_NAME = "p3win.db";
    public static final int DB_VERSION = 1;

    private static P3Database instance;

    private P3Database(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    public static synchronized P3Database get(Context context) {
        if (instance == null && context != null) {
            instance = new P3Database(context);
        }
        return instance;
    }

    public static P3Database get() {
        return instance;
    }

    /** Открыть базу заранее, чтобы поймать ошибки на старте приложения. */
    public void open() {
        getWritableDatabase();
    }

    public static synchronized void release() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    // ------------------------------------------------------------------
    // Схема
    // ------------------------------------------------------------------

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE container_meta ("
                + "id INTEGER PRIMARY KEY,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "profileId TEXT NOT NULL DEFAULT 'balanced',"
                + "box64Preset TEXT NOT NULL DEFAULT 'PERFORMANCE',"
                + "wineVersion TEXT NOT NULL DEFAULT '',"
                + "graphicsDriver TEXT NOT NULL DEFAULT 'turnip',"
                + "note TEXT NOT NULL DEFAULT '',"
                + "accentColor INTEGER NOT NULL DEFAULT 0,"
                + "favorite INTEGER NOT NULL DEFAULT 0,"
                + "createdAt INTEGER NOT NULL DEFAULT 0,"
                + "lastUsedAt INTEGER NOT NULL DEFAULT 0,"
                + "launchCount INTEGER NOT NULL DEFAULT 0,"
                + "totalPlaytimeMs INTEGER NOT NULL DEFAULT 0,"
                + "sizeBytes INTEGER NOT NULL DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE games ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "containerId INTEGER NOT NULL DEFAULT 0,"
                + "executable TEXT NOT NULL DEFAULT '',"
                + "arguments TEXT NOT NULL DEFAULT '',"
                + "shortcutName TEXT NOT NULL DEFAULT '',"
                + "tags TEXT NOT NULL DEFAULT '',"
                + "description TEXT NOT NULL DEFAULT '',"
                + "releaseYear INTEGER NOT NULL DEFAULT 0,"
                + "profileId INTEGER NOT NULL DEFAULT 0,"
                + "favorite INTEGER NOT NULL DEFAULT 0,"
                + "hidden INTEGER NOT NULL DEFAULT 0,"
                + "totalPlaytimeMs INTEGER NOT NULL DEFAULT 0,"
                + "lastPlayedAt INTEGER NOT NULL DEFAULT 0,"
                + "addedAt INTEGER NOT NULL DEFAULT 0,"
                + "posterFile TEXT NOT NULL DEFAULT '',"
                + "coverFile TEXT NOT NULL DEFAULT '',"
                + "heroFile TEXT NOT NULL DEFAULT '',"
                + "rating INTEGER NOT NULL DEFAULT 0,"
                + "compatibility INTEGER NOT NULL DEFAULT 0"
                + ")");
        db.execSQL("CREATE INDEX idx_games_container ON games(containerId)");
        db.execSQL("CREATE INDEX idx_games_shortcut ON games(shortcutName)");

        db.execSQL("CREATE TABLE game_profiles ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "graphicsDriver TEXT NOT NULL DEFAULT '',"
                + "graphicsDriverConfig TEXT NOT NULL DEFAULT '',"
                + "dxWrapper TEXT NOT NULL DEFAULT '',"
                + "dxWrapperConfig TEXT NOT NULL DEFAULT '',"
                + "screenSize TEXT NOT NULL DEFAULT '',"
                + "box64Preset TEXT NOT NULL DEFAULT '',"
                + "envVars TEXT NOT NULL DEFAULT '',"
                + "fpsLimit INTEGER NOT NULL DEFAULT 0,"
                + "fsrEnabled INTEGER NOT NULL DEFAULT 0,"
                + "fsrSharpness INTEGER NOT NULL DEFAULT 2,"
                + "controlsProfileId INTEGER NOT NULL DEFAULT 0,"
                + "dinputMapperType INTEGER NOT NULL DEFAULT 1,"
                + "inputLayout TEXT NOT NULL DEFAULT '',"
                + "forceFullscreen INTEGER NOT NULL DEFAULT 1,"
                + "audioDriver TEXT NOT NULL DEFAULT '',"
                + "audioDriverConfig TEXT NOT NULL DEFAULT '',"
                + "createdAt INTEGER NOT NULL DEFAULT 0,"
                + "updatedAt INTEGER NOT NULL DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE graphics_drivers ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "driverId TEXT NOT NULL DEFAULT '',"
                + "version TEXT NOT NULL DEFAULT '',"
                + "displayName TEXT NOT NULL DEFAULT '',"
                + "vendor TEXT NOT NULL DEFAULT '',"
                + "minAdrenoModel INTEGER NOT NULL DEFAULT 0,"
                + "maxAdrenoModel INTEGER NOT NULL DEFAULT 0,"
                + "supportsGen8 INTEGER NOT NULL DEFAULT 0,"
                + "verifiedOnReference INTEGER NOT NULL DEFAULT 0,"
                + "compatibilityLevel INTEGER NOT NULL DEFAULT 0,"
                + "builtin INTEGER NOT NULL DEFAULT 0,"
                + "installed INTEGER NOT NULL DEFAULT 0,"
                + "active INTEGER NOT NULL DEFAULT 0,"
                + "installedAt INTEGER NOT NULL DEFAULT 0,"
                + "warning TEXT NOT NULL DEFAULT ''"
                + ")");
        db.execSQL("CREATE UNIQUE INDEX idx_driver_unique ON graphics_drivers(driverId, version)");

        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "gameId INTEGER NOT NULL DEFAULT 0,"
                + "containerId INTEGER NOT NULL DEFAULT 0,"
                + "executable TEXT NOT NULL DEFAULT '',"
                + "startedAt INTEGER NOT NULL DEFAULT 0,"
                + "endedAt INTEGER NOT NULL DEFAULT 0,"
                + "durationMs INTEGER NOT NULL DEFAULT 0,"
                + "activeMs INTEGER NOT NULL DEFAULT 0,"
                + "crashed INTEGER NOT NULL DEFAULT 0,"
                + "exitCode INTEGER NOT NULL DEFAULT 0,"
                + "profileSnapshot TEXT NOT NULL DEFAULT '',"
                + "failureReason TEXT NOT NULL DEFAULT ''"
                + ")");
        db.execSQL("CREATE INDEX idx_sessions_game ON sessions(gameId)");
        db.execSQL("CREATE INDEX idx_sessions_started ON sessions(startedAt)");

        db.execSQL("CREATE TABLE input_layouts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "name TEXT NOT NULL DEFAULT '',"
                + "baseProfile TEXT NOT NULL DEFAULT '',"
                + "gameId INTEGER NOT NULL DEFAULT 0,"
                + "opacity REAL NOT NULL DEFAULT 1.0,"
                + "scale REAL NOT NULL DEFAULT 1.0,"
                + "hapticFeedback INTEGER NOT NULL DEFAULT 1,"
                + "deadZone REAL NOT NULL DEFAULT 0.15,"
                + "vendorId INTEGER NOT NULL DEFAULT 0,"
                + "productId INTEGER NOT NULL DEFAULT 0,"
                + "createdAt INTEGER NOT NULL DEFAULT 0,"
                + "updatedAt INTEGER NOT NULL DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE milestones ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "key TEXT NOT NULL UNIQUE,"
                + "title TEXT NOT NULL DEFAULT '',"
                + "description TEXT NOT NULL DEFAULT '',"
                + "achieved INTEGER NOT NULL DEFAULT 0,"
                + "achievedAt INTEGER NOT NULL DEFAULT 0,"
                + "progress INTEGER NOT NULL DEFAULT 0"
                + ")");

        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Миграции добавляются пошагово. Правило проекта: никогда не удаляем
        // пользовательские данные при апгрейде схемы — только ALTER/перенос.
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
        try {
            db.execSQL("PRAGMA journal_mode=WAL");
        } catch (Exception ignored) {
            // Некоторые прошивки не дают включить WAL — не критично.
        }
    }

    // ------------------------------------------------------------------
    // DAO
    // ------------------------------------------------------------------

    public ContainerMetaDao containerDao() {
        return new ContainerMetaDao(getWritableDatabase());
    }

    public GameDao gameDao() {
        return new GameDao(getWritableDatabase());
    }

    public GameProfileDao gameProfileDao() {
        return new GameProfileDao(getWritableDatabase());
    }

    public GraphicsDriverDao graphicsDriverDao() {
        return new GraphicsDriverDao(getWritableDatabase());
    }

    public SessionDao sessionDao() {
        return new SessionDao(getWritableDatabase());
    }

    public InputLayoutDao inputLayoutDao() {
        return new InputLayoutDao(getWritableDatabase());
    }

    public MilestoneDao milestoneDao() {
        return new MilestoneDao(getWritableDatabase());
    }

    // ------------------------------------------------------------------
    // Служебная таблица
    // ------------------------------------------------------------------

    public String meta(String key) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT value FROM meta WHERE key = ?", new String[]{key})) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    public void setMeta(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value != null ? value : "");
        getWritableDatabase().insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
