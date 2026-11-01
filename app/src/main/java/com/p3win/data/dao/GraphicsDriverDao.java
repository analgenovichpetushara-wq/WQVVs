package com.p3win.data.dao;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.p3win.data.entity.GraphicsDriver;

import java.util.ArrayList;
import java.util.List;

/** DAO репозитория GPU-драйверов (P3 GPU Manager). */
public class GraphicsDriverDao {

    private final SQLiteDatabase db;

    public GraphicsDriverDao(SQLiteDatabase db) {
        this.db = db;
    }

    public void upsert(GraphicsDriver driver) {
        db.insertWithOnConflict("graphics_drivers", null, toValues(driver),
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<GraphicsDriver> findAll() {
        ArrayList<GraphicsDriver> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM graphics_drivers ORDER BY driverId ASC, installed DESC, version DESC",
                null)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<GraphicsDriver> findByDriver(String driverId) {
        ArrayList<GraphicsDriver> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM graphics_drivers WHERE driverId = ? ORDER BY version DESC",
                new String[]{driverId})) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public List<GraphicsDriver> installed() {
        ArrayList<GraphicsDriver> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM graphics_drivers WHERE installed = 1 ORDER BY driverId, version DESC",
                null)) {
            while (cursor.moveToNext()) result.add(fromCursor(cursor));
        }
        return result;
    }

    public GraphicsDriver findActive() {
        try (Cursor cursor = db.rawQuery("SELECT * FROM graphics_drivers WHERE active = 1 LIMIT 1", null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public GraphicsDriver find(String driverId, String version) {
        try (Cursor cursor = db.rawQuery(
                "SELECT * FROM graphics_drivers WHERE driverId = ? AND version = ? LIMIT 1",
                new String[]{driverId, version})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public void clearActive() {
        ContentValues values = new ContentValues();
        values.put("active", 0);
        db.update("graphics_drivers", values, "active = 1", null);
    }

    public void setActive(String driverId, String version) {
        db.beginTransaction();
        try {
            clearActive();
            ContentValues values = new ContentValues();
            values.put("active", 1);
            values.put("installed", 1);
            values.put("installedAt", System.currentTimeMillis());
            db.update("graphics_drivers", values, "driverId = ? AND version = ?",
                    new String[]{driverId, version});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void setInstalled(String driverId, String version, boolean installed) {
        ContentValues values = new ContentValues();
        values.put("installed", installed ? 1 : 0);
        values.put("installedAt", installed ? System.currentTimeMillis() : 0);
        if (!installed) values.put("active", 0);
        db.update("graphics_drivers", values, "driverId = ? AND version = ?",
                new String[]{driverId, version});
    }

    public void delete(String driverId, String version) {
        db.delete("graphics_drivers", "driverId = ? AND version = ?", new String[]{driverId, version});
    }

    public int count() {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM graphics_drivers", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private ContentValues toValues(GraphicsDriver driver) {
        ContentValues values = new ContentValues();
        values.put("driverId", driver.driverId);
        values.put("version", driver.version);
        values.put("displayName", driver.displayName);
        values.put("vendor", driver.vendor);
        values.put("minAdrenoModel", driver.minAdrenoModel);
        values.put("maxAdrenoModel", driver.maxAdrenoModel);
        values.put("supportsGen8", driver.supportsGen8 ? 1 : 0);
        values.put("verifiedOnReference", driver.verifiedOnReference ? 1 : 0);
        values.put("compatibilityLevel", driver.compatibilityLevel);
        values.put("builtin", driver.builtin ? 1 : 0);
        values.put("installed", driver.installed ? 1 : 0);
        values.put("active", driver.active ? 1 : 0);
        values.put("installedAt", driver.installedAt);
        values.put("warning", driver.warning);
        return values;
    }

    private GraphicsDriver fromCursor(Cursor cursor) {
        GraphicsDriver driver = new GraphicsDriver();
        driver.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        driver.driverId = cursor.getString(cursor.getColumnIndexOrThrow("driverId"));
        driver.version = cursor.getString(cursor.getColumnIndexOrThrow("version"));
        driver.displayName = cursor.getString(cursor.getColumnIndexOrThrow("displayName"));
        driver.vendor = cursor.getString(cursor.getColumnIndexOrThrow("vendor"));
        driver.minAdrenoModel = cursor.getInt(cursor.getColumnIndexOrThrow("minAdrenoModel"));
        driver.maxAdrenoModel = cursor.getInt(cursor.getColumnIndexOrThrow("maxAdrenoModel"));
        driver.supportsGen8 = cursor.getInt(cursor.getColumnIndexOrThrow("supportsGen8")) != 0;
        driver.verifiedOnReference = cursor.getInt(cursor.getColumnIndexOrThrow("verifiedOnReference")) != 0;
        driver.compatibilityLevel = cursor.getInt(cursor.getColumnIndexOrThrow("compatibilityLevel"));
        driver.builtin = cursor.getInt(cursor.getColumnIndexOrThrow("builtin")) != 0;
        driver.installed = cursor.getInt(cursor.getColumnIndexOrThrow("installed")) != 0;
        driver.active = cursor.getInt(cursor.getColumnIndexOrThrow("active")) != 0;
        driver.installedAt = cursor.getLong(cursor.getColumnIndexOrThrow("installedAt"));
        driver.warning = cursor.getString(cursor.getColumnIndexOrThrow("warning"));
        return driver;
    }
}
