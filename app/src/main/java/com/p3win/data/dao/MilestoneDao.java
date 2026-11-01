package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.Milestone;

import java.util.ArrayList;
import java.util.List;

/** DAO вех/достижений главного экрана. */
public class MilestoneDao {

    private final SQLiteDatabase db;

    public MilestoneDao(SQLiteDatabase db) {
        this.db = db;
    }

    public void upsert(Milestone milestone) {
        ContentValues values = new ContentValues();
        values.put("key", milestone.key);
        values.put("title", milestone.title);
        values.put("description", milestone.description);
        values.put("achieved", milestone.achieved ? 1 : 0);
        values.put("achievedAt", milestone.achievedAt);
        values.put("progress", milestone.progress);
        db.insertWithOnConflict("milestones", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Milestone findByKey(String key) {
        try (Cursor cursor = db.rawQuery("SELECT * FROM milestones WHERE key = ? LIMIT 1",
                new String[]{key})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public List<Milestone> findAll() {
        ArrayList<Milestone> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM milestones ORDER BY achieved DESC, progress DESC", null)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    /** Отметить веху достигнутой; создаёт запись, если её ещё нет. */
    public void markAchieved(String key, String title, long whenMs) {
        ContentValues values = new ContentValues();
        values.put("achieved", 1);
        values.put("achievedAt", whenMs);
        values.put("progress", 100);
        int updated = db.update("milestones", values, "key = ?", new String[]{key});
        if (updated == 0) {
            Milestone milestone = new Milestone();
            milestone.key = key;
            milestone.title = title != null ? title : "";
            milestone.achieved = true;
            milestone.achievedAt = whenMs;
            milestone.progress = 100;
            upsert(milestone);
        }
    }

    public void setProgress(String key, String title, String description, int progress) {
        Milestone existing = findByKey(key);
        if (existing != null && existing.achieved) return;
        Milestone milestone = existing != null ? existing : new Milestone();
        milestone.key = key;
        if (title != null) milestone.title = title;
        if (description != null) milestone.description = description;
        milestone.progress = Math.max(0, Math.min(100, progress));
        upsert(milestone);
    }

    private Milestone fromCursor(Cursor cursor) {
        Milestone milestone = new Milestone();
        milestone.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        milestone.key = cursor.getString(cursor.getColumnIndexOrThrow("key"));
        milestone.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
        milestone.description = cursor.getString(cursor.getColumnIndexOrThrow("description"));
        milestone.achieved = cursor.getInt(cursor.getColumnIndexOrThrow("achieved")) != 0;
        milestone.achievedAt = cursor.getLong(cursor.getColumnIndexOrThrow("achievedAt"));
        milestone.progress = cursor.getInt(cursor.getColumnIndexOrThrow("progress"));
        return milestone;
    }
}
