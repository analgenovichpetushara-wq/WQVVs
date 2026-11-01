package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.GameProfile;

import java.util.ArrayList;
import java.util.List;

/** DAO профилей запуска игр. */
public class GameProfileDao {

    private final SQLiteDatabase db;

    public GameProfileDao(SQLiteDatabase db) {
        this.db = db;
    }

    public long insert(GameProfile profile) {
        return db.insert("game_profiles", null, toValues(profile, false));
    }

    public void update(GameProfile profile) {
        profile.updatedAt = System.currentTimeMillis();
        db.update("game_profiles", toValues(profile, true), "id = ?",
                new String[]{String.valueOf(profile.id)});
    }

    public void delete(long id) {
        db.delete("game_profiles", "id = ?", new String[]{String.valueOf(id)});
    }

    public GameProfile findById(long id) {
        if (id <= 0) return null;
        try (Cursor cursor = db.rawQuery("SELECT * FROM game_profiles WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<GameProfile> findAll() {
        ArrayList<GameProfile> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM game_profiles ORDER BY updatedAt DESC", null)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public GameProfile findByName(String name) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM game_profiles WHERE name = ? LIMIT 1",
                new String[]{name})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public long duplicate(long sourceId, String newName) {
        GameProfile source = findById(sourceId);
        if (source == null) return -1;
        source.id = 0;
        source.name = newName != null ? newName : source.name + " (копия)";
        source.createdAt = System.currentTimeMillis();
        source.updatedAt = source.createdAt;
        return insert(source);
    }

    public int count() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM game_profiles", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private ContentValues toValues(GameProfile profile, boolean includeId) {
        ContentValues values = new ContentValues();
        if (includeId) values.put("id", profile.id);
        values.put("name", profile.name);
        values.put("graphicsDriver", profile.graphicsDriver);
        values.put("graphicsDriverConfig", profile.graphicsDriverConfig);
        values.put("dxWrapper", profile.dxWrapper);
        values.put("dxWrapperConfig", profile.dxWrapperConfig);
        values.put("screenSize", profile.screenSize);
        values.put("box64Preset", profile.box64Preset);
        values.put("envVars", profile.envVars);
        values.put("fpsLimit", profile.fpsLimit);
        values.put("fsrEnabled", profile.fsrEnabled ? 1 : 0);
        values.put("fsrSharpness", profile.fsrSharpness);
        values.put("controlsProfileId", profile.controlsProfileId);
        values.put("dinputMapperType", profile.dinputMapperType);
        values.put("inputLayout", profile.inputLayout);
        values.put("forceFullscreen", profile.forceFullscreen ? 1 : 0);
        values.put("audioDriver", profile.audioDriver);
        values.put("audioDriverConfig", profile.audioDriverConfig);
        values.put("createdAt", profile.createdAt > 0 ? profile.createdAt : System.currentTimeMillis());
        values.put("updatedAt", profile.updatedAt > 0 ? profile.updatedAt : System.currentTimeMillis());
        return values;
    }

    private GameProfile fromCursor(Cursor cursor) {
        GameProfile profile = new GameProfile();
        profile.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        profile.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        profile.graphicsDriver = cursor.getString(cursor.getColumnIndexOrThrow("graphicsDriver"));
        profile.graphicsDriverConfig = cursor.getString(cursor.getColumnIndexOrThrow("graphicsDriverConfig"));
        profile.dxWrapper = cursor.getString(cursor.getColumnIndexOrThrow("dxWrapper"));
        profile.dxWrapperConfig = cursor.getString(cursor.getColumnIndexOrThrow("dxWrapperConfig"));
        profile.screenSize = cursor.getString(cursor.getColumnIndexOrThrow("screenSize"));
        profile.box64Preset = cursor.getString(cursor.getColumnIndexOrThrow("box64Preset"));
        profile.envVars = cursor.getString(cursor.getColumnIndexOrThrow("envVars"));
        profile.fpsLimit = cursor.getInt(cursor.getColumnIndexOrThrow("fpsLimit"));
        profile.fsrEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("fsrEnabled")) != 0;
        profile.fsrSharpness = cursor.getInt(cursor.getColumnIndexOrThrow("fsrSharpness"));
        profile.controlsProfileId = cursor.getInt(cursor.getColumnIndexOrThrow("controlsProfileId"));
        profile.dinputMapperType = cursor.getInt(cursor.getColumnIndexOrThrow("dinputMapperType"));
        profile.inputLayout = cursor.getString(cursor.getColumnIndexOrThrow("inputLayout"));
        profile.forceFullscreen = cursor.getInt(cursor.getColumnIndexOrThrow("forceFullscreen")) != 0;
        profile.audioDriver = cursor.getString(cursor.getColumnIndexOrThrow("audioDriver"));
        profile.audioDriverConfig = cursor.getString(cursor.getColumnIndexOrThrow("audioDriverConfig"));
        profile.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"));
        profile.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt"));
        return profile;
    }
}
