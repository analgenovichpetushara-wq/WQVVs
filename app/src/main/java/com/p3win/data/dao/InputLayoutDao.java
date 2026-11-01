package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.InputLayout;

import java.util.ArrayList;
import java.util.List;

/** DAO раскладок ввода P3 Input Manager. */
public class InputLayoutDao {

    private final SQLiteDatabase db;

    public InputLayoutDao(SQLiteDatabase db) {
        this.db = db;
    }

    public long insert(InputLayout layout) {
        return db.insert("input_layouts", null, toValues(layout, false));
    }

    public void update(InputLayout layout) {
        db.update("input_layouts", toValues(layout, true), "id = ?",
                new String[]{String.valueOf(layout.id)});
    }

    public void delete(long id) {
        db.delete("input_layouts", "id = ?", new String[]{String.valueOf(id)});
    }

    public InputLayout findById(long id) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM input_layouts WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    /** Раскладка для игры; null — используется глобальная. */
    public InputLayout forGame(long gameId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM input_layouts WHERE gameId = ? ORDER BY updatedAt DESC LIMIT 1",
                new String[]{String.valueOf(gameId)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public InputLayout globalLayout() {
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM input_layouts WHERE gameId = 0 ORDER BY updatedAt DESC LIMIT 1", null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<InputLayout> findAll() {
        ArrayList<InputLayout> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM input_layouts ORDER BY updatedAt DESC", null)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    private ContentValues toValues(InputLayout layout, boolean includeId) {
        ContentValues values = new ContentValues();
        if (includeId) values.put("id", layout.id);
        values.put("name", layout.name);
        values.put("baseProfile", layout.baseProfile);
        values.put("gameId", layout.gameId);
        values.put("opacity", layout.opacity);
        values.put("scale", layout.scale);
        values.put("hapticFeedback", layout.hapticFeedback ? 1 : 0);
        values.put("deadZone", layout.deadZone);
        values.put("vendorId", layout.vendorId);
        values.put("productId", layout.productId);
        values.put("createdAt", layout.createdAt > 0 ? layout.createdAt : System.currentTimeMillis());
        values.put("updatedAt", System.currentTimeMillis());
        return values;
    }

    private InputLayout fromCursor(Cursor cursor) {
        InputLayout layout = new InputLayout();
        layout.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        layout.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
        layout.baseProfile = cursor.getString(cursor.getColumnIndexOrThrow("baseProfile"));
        layout.gameId = cursor.getLong(cursor.getColumnIndexOrThrow("gameId"));
        layout.opacity = cursor.getFloat(cursor.getColumnIndexOrThrow("opacity"));
        layout.scale = cursor.getFloat(cursor.getColumnIndexOrThrow("scale"));
        layout.hapticFeedback = cursor.getInt(cursor.getColumnIndexOrThrow("hapticFeedback")) != 0;
        layout.deadZone = cursor.getFloat(cursor.getColumnIndexOrThrow("deadZone"));
        layout.vendorId = cursor.getInt(cursor.getColumnIndexOrThrow("vendorId"));
        layout.productId = cursor.getInt(cursor.getColumnIndexOrThrow("productId"));
        layout.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"));
        layout.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt"));
        return layout;
    }
}
