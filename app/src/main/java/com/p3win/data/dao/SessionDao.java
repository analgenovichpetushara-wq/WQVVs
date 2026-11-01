package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.GameSession;

import java.util.ArrayList;
import java.util.List;

/** DAO игровых сессий: пишет SessionTracker, читают Home и P3 Engine. */
public class SessionDao {

    private final SQLiteDatabase db;

    public SessionDao(SQLiteDatabase db) {
        this.db = db;
    }

    public long insert(GameSession session) {
        ContentValues values = new ContentValues();
        values.put("gameId", session.gameId);
        values.put("containerId", session.containerId);
        values.put("executable", session.executable);
        values.put("startedAt", session.startedAt);
        values.put("endedAt", session.endedAt);
        values.put("durationMs", session.durationMs);
        values.put("activeMs", session.activeMs);
        values.put("crashed", session.crashed ? 1 : 0);
        values.put("exitCode", session.exitCode);
        values.put("profileSnapshot", session.profileSnapshot);
        values.put("failureReason", session.failureReason);
        return db.insert("sessions", null, values);
    }

    public void updateActive(long id, long activeMs, long updatedAt) {
        ContentValues values = new ContentValues();
        values.put("activeMs", activeMs);
        values.put("endedAt", updatedAt);
        db.update("sessions", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void finish(long id, long endedAt, long durationMs, long activeMs, boolean crashed, int exitCode) {
        ContentValues values = new ContentValues();
        values.put("endedAt", endedAt);
        values.put("durationMs", durationMs);
        values.put("activeMs", activeMs);
        values.put("crashed", crashed ? 1 : 0);
        values.put("exitCode", exitCode);
        db.update("sessions", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void setFailureReason(long id, String reason) {
        ContentValues values = new ContentValues();
        values.put("failureReason", reason != null ? reason : "");
        db.update("sessions", values, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<GameSession> recent(int limit) {
        ArrayList<GameSession> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM sessions ORDER BY startedAt DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<GameSession> forGame(long gameId, int limit) {
        ArrayList<GameSession> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM sessions WHERE gameId = ? ORDER BY startedAt DESC LIMIT ?",
                new String[]{String.valueOf(gameId), String.valueOf(limit)})) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public int countSince(long sinceMs) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM sessions WHERE startedAt >= ?",
                new String[]{String.valueOf(sinceMs)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public long activeTimeSince(long sinceMs) {
        try (Cursor cursor = db.rawQuery("SELECT SUM(activeMs) FROM sessions WHERE startedAt >= ?",
                new String[]{String.valueOf(sinceMs)})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public int crashCount() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM sessions WHERE crashed = 1", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Ближайшая по времени сессия — для сопоставления сбоя с логом. */
    public GameSession findClosest(long timestampMs) {
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM sessions ORDER BY ABS(startedAt - ?) ASC LIMIT 1",
                new String[]{String.valueOf(timestampMs)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public void prune(int keepLast) {
        db.execSQL("DELETE FROM sessions WHERE id NOT IN "
                + "(SELECT id FROM sessions ORDER BY startedAt DESC LIMIT ?)", new Object[]{keepLast});
    }

    private GameSession fromCursor(Cursor cursor) {
        GameSession session = new GameSession();
        session.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        session.gameId = cursor.getLong(cursor.getColumnIndexOrThrow("gameId"));
        session.containerId = cursor.getLong(cursor.getColumnIndexOrThrow("containerId"));
        session.executable = cursor.getString(cursor.getColumnIndexOrThrow("executable"));
        session.startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("startedAt"));
        session.endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("endedAt"));
        session.durationMs = cursor.getLong(cursor.getColumnIndexOrThrow("durationMs"));
        session.activeMs = cursor.getLong(cursor.getColumnIndexOrThrow("activeMs"));
        session.crashed = cursor.getInt(cursor.getColumnIndexOrThrow("crashed")) != 0;
        session.exitCode = cursor.getInt(cursor.getColumnIndexOrThrow("exitCode"));
        session.profileSnapshot = cursor.getString(cursor.getColumnIndexOrThrow("profileSnapshot"));
        session.failureReason = cursor.getString(cursor.getColumnIndexOrThrow("failureReason"));
        return session;
    }
}
