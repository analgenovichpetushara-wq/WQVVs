package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.Game;

import java.util.ArrayList;
import java.util.List;

/** DAO библиотеки игр. */
public class GameDao {

    /** Поле сортировки библиотеки. */
    public enum Sort {TITLE, LAST_PLAYED, PLAYTIME, ADDED, RATING}

    private final SQLiteDatabase db;

    public GameDao(SQLiteDatabase db) {
        this.db = db;
    }

    public long insert(Game game) {
        return db.insert("games", null, toValues(game, false));
    }

    public void update(Game game) {
        db.update("games", toValues(game, true), "id = ?", new String[]{String.valueOf(game.id)});
    }

    public void delete(long id) {
        db.delete("games", "id = ?", new String[]{String.valueOf(id)});
    }

    public Game findById(long id) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM games WHERE id = ?", new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public Game findByShortcut(String shortcutName) {
        if (shortcutName == null || shortcutName.isEmpty()) return null;
        try (Cursor cursor = db.rawQuery("SELECT * FROM games WHERE shortcutName = ? LIMIT 1",
                new String[]{shortcutName})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public Game findByExecutable(String executable) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM games WHERE executable = ? LIMIT 1",
                new String[]{executable})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    /** Список с поиском, фильтром по контейнеру/избранному и сортировкой. */
    public List<Game> query(String search, long containerId, boolean onlyFavorites, Sort sort) {
        StringBuilder sql = new StringBuilder("SELECT * FROM games WHERE hidden = 0");
        ArrayList<String> args = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (title LIKE ? OR tags LIKE ?)");
            String like = "%" + search.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (containerId > 0) {
            sql.append(" AND containerId = ?");
            args.add(String.valueOf(containerId));
        }
        if (onlyFavorites) sql.append(" AND favorite = 1");

        switch (sort != null ? sort : Sort.TITLE) {
            case LAST_PLAYED:
                sql.append(" ORDER BY lastPlayedAt DESC, title COLLATE NOCASE ASC");
                break;
            case PLAYTIME:
                sql.append(" ORDER BY totalPlaytimeMs DESC, title COLLATE NOCASE ASC");
                break;
            case ADDED:
                sql.append(" ORDER BY addedAt DESC, title COLLATE NOCASE ASC");
                break;
            case RATING:
                sql.append(" ORDER BY rating DESC, title COLLATE NOCASE ASC");
                break;
            case TITLE:
            default:
                sql.append(" ORDER BY title COLLATE NOCASE ASC");
                break;
        }

        ArrayList<Game> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Game> recentlyPlayed(int limit) {
        ArrayList<Game> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM games WHERE lastPlayedAt > 0 AND hidden = 0 "
                        + "ORDER BY lastPlayedAt DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Game> favorites(int limit) {
        ArrayList<Game> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM games WHERE favorite = 1 AND hidden = 0 "
                        + "ORDER BY title COLLATE NOCASE LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<Game> findByContainer(long containerId) {
        ArrayList<Game> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM games WHERE containerId = ? ORDER BY title COLLATE NOCASE",
                new String[]{String.valueOf(containerId)})) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public void setFavorite(long id, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put("favorite", favorite ? 1 : 0);
        db.update("games", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void touch(long id) {
        db.execSQL("UPDATE games SET lastPlayedAt = ? WHERE id = ?",
                new Object[]{System.currentTimeMillis(), id});
    }

    public void addPlaytime(long id, long deltaMs) {
        db.execSQL("UPDATE games SET totalPlaytimeMs = totalPlaytimeMs + ?, lastPlayedAt = ? WHERE id = ?",
                new Object[]{deltaMs, System.currentTimeMillis(), id});
    }

    public void setArtwork(long id, String poster, String cover, String hero) {
        ContentValues values = new ContentValues();
        values.put("posterFile", poster != null ? poster : "");
        values.put("coverFile", cover != null ? cover : "");
        values.put("heroFile", hero != null ? hero : "");
        db.update("games", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public int count() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM games WHERE hidden = 0", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public long totalPlaytime() {
        try (Cursor cursor = db.rawQuery("SELECT SUM(totalPlaytimeMs) FROM games", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    private ContentValues toValues(Game game, boolean includeId) {
        ContentValues values = new ContentValues();
        if (includeId) values.put("id", game.id);
        values.put("title", game.title);
        values.put("containerId", game.containerId);
        values.put("executable", game.executable);
        values.put("arguments", game.arguments);
        values.put("shortcutName", game.shortcutName);
        values.put("tags", game.tags);
        values.put("description", game.description);
        values.put("releaseYear", game.releaseYear);
        values.put("profileId", game.profileId);
        values.put("favorite", game.favorite ? 1 : 0);
        values.put("hidden", game.hidden ? 1 : 0);
        values.put("totalPlaytimeMs", game.totalPlaytimeMs);
        values.put("lastPlayedAt", game.lastPlayedAt);
        values.put("addedAt", game.addedAt > 0 ? game.addedAt : System.currentTimeMillis());
        values.put("posterFile", game.posterFile);
        values.put("coverFile", game.coverFile);
        values.put("heroFile", game.heroFile);
        values.put("rating", game.rating);
        values.put("compatibility", game.compatibility);
        return values;
    }

    private Game fromCursor(Cursor cursor) {
        Game game = new Game();
        game.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        game.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        game.containerId = cursor.getLong(cursor.getColumnIndexOrThrow("containerId"));
        game.executable = cursor.getString(cursor.getColumnIndexOrThrow("executable"));
        game.arguments = cursor.getString(cursor.getColumnIndexOrThrow("arguments"));
        game.shortcutName = cursor.getString(cursor.getColumnIndexOrThrow("shortcutName"));
        game.tags = cursor.getString(cursor.getColumnIndexOrThrow("tags"));
        game.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
        game.releaseYear = cursor.getInt(cursor.getColumnIndexOrThrow("releaseYear"));
        game.profileId = cursor.getLong(cursor.getColumnIndexOrThrow("profileId"));
        game.favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0;
        game.hidden = cursor.getInt(cursor.getColumnIndexOrThrow("hidden")) != 0;
        game.totalPlaytimeMs = cursor.getLong(cursor.getColumnIndexOrThrow("totalPlaytimeMs"));
        game.lastPlayedAt = cursor.getLong(cursor.getColumnIndexOrThrow("lastPlayedAt"));
        game.addedAt = cursor.getLong(cursor.getColumnIndexOrThrow("addedAt"));
        game.posterFile = cursor.getString(cursor.getColumnIndexOrThrow("posterFile"));
        game.coverFile = cursor.getString(cursor.getColumnIndexOrThrow("coverFile"));
        game.heroFile = cursor.getString(cursor.getColumnIndexOrThrow("heroFile"));
        game.rating = cursor.getInt(cursor.getColumnIndexOrThrow("rating"));
        game.compatibility = cursor.getInt(cursor.getColumnIndexOrThrow("compatibility"));
        return game;
    }
}
