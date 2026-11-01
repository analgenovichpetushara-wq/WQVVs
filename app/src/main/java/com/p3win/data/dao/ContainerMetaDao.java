package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.ContainerMeta;

import java.util.ArrayList;
import java.util.List;

/** DAO метаданных контейнеров. Блокирующие вызовы — только из фонового потока. */
public class ContainerMetaDao {

    private final SQLiteDatabase db;

    public ContainerMetaDao(SQLiteDatabase db) {
        this.db = db;
    }

    public void upsert(ContainerMeta meta) {
        db.insertWithOnConflict("container_meta", null, toValues(meta), SQLiteDatabase.CONFLICT_REPLACE);
    }

    public ContainerMeta findById(long id) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM container_meta WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<ContainerMeta> findAll() {
        ArrayList<ContainerMeta> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM container_meta ORDER BY favorite DESC, lastUsedAt DESC, name ASC", null)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public void delete(long id) {
        db.delete("container_meta", "id = ?", new String[]{String.valueOf(id)});
    }

    public void setFavorite(long id, boolean favorite) {
        ContentValues values = new ContentValues();
        values.put("favorite", favorite ? 1 : 0);
        db.update("container_meta", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void touch(long id) {
        db.execSQL("UPDATE container_meta SET lastUsedAt = ?, launchCount = launchCount + 1 WHERE id = ?",
                new Object[]{System.currentTimeMillis(), id});
    }

    public void addPlaytime(long id, long deltaMs) {
        db.execSQL("UPDATE container_meta SET totalPlaytimeMs = totalPlaytimeMs + ?, lastUsedAt = ? WHERE id = ?",
                new Object[]{deltaMs, System.currentTimeMillis(), id});
    }

    public void setSize(long id, long sizeBytes) {
        ContentValues values = new ContentValues();
        values.put("sizeBytes", sizeBytes);
        db.update("container_meta", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public int count() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM container_meta", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private ContentValues toValues(ContainerMeta meta) {
        ContentValues values = new ContentValues();
        values.put("id", meta.id);
        values.put("name", meta.name);
        values.put("profileId", meta.profileId);
        values.put("box64Preset", meta.box64Preset);
        values.put("wineVersion", meta.wineVersion);
        values.put("graphicsDriver", meta.graphicsDriver);
        values.put("note", meta.note);
        values.put("accentColor", meta.accentColor);
        values.put("favorite", meta.favorite ? 1 : 0);
        values.put("createdAt", meta.createdAt);
        values.put("lastUsedAt", meta.lastUsedAt);
        values.put("launchCount", meta.launchCount);
        values.put("totalPlaytimeMs", meta.totalPlaytimeMs);
        values.put("sizeBytes", meta.sizeBytes);
        return values;
    }

    private ContainerMeta fromCursor(Cursor cursor) {
        ContainerMeta meta = new ContainerMeta();
        meta.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        meta.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        meta.profileId = cursor.getString(cursor.getColumnIndexOrThrow("profileId"));
        meta.box64Preset = cursor.getString(cursor.getColumnIndexOrThrow("box64Preset"));
        meta.wineVersion = cursor.getString(cursor.getColumnIndexOrThrow("wineVersion"));
        meta.graphicsDriver = cursor.getString(cursor.getColumnIndexOrThrow("graphicsDriver"));
        meta.note = cursor.getString(cursor.getColumnIndexOrThrow("note"));
        meta.accentColor = cursor.getInt(cursor.getColumnIndexOrThrow("accentColor"));
        meta.favorite = cursor.getInt(cursor.getColumnIndexOrThrow("favorite")) != 0;
        meta.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"));
        meta.lastUsedAt = cursor.getLong(cursor.getColumnIndexOrThrow("lastUsedAt"));
        meta.launchCount = cursor.getInt(cursor.getColumnIndexOrThrow("launchCount"));
        meta.totalPlaytimeMs = cursor.getLong(cursor.getColumnIndexOrThrow("totalPlaytimeMs"));
        meta.sizeBytes = cursor.getLong(cursor.getColumnIndexOrThrow("sizeBytes"));
        return meta;
    }
}
