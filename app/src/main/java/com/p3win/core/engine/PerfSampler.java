package com.p3win.core.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;

import com.p3win.core.log.P3Log;
import com.winlator.core.CPUStatus;

import java.io.RandomAccessFile;

/**
 * Сборщик метрик производительности для P3 Engine.
 *
 * <h3>Что меряем и как</h3>
 * <ul>
 *   <li><b>FPS</b> — по счётчику кадров отрисовки (передаёт сам рендерер ядра
 *       через {@link #onFrame()}); отдельный «счётчик FPS» без доступа
 *       к рендереру был бы выдумкой.</li>
 *   <li><b>Frame time</b> — производная от FPS: среднее время кадра
 *       за окно измерения.</li>
 *   <li><b>RAM</b> — {@link ActivityManager.MemoryInfo} + размер heap процесса.</li>
 *   <li><b>CPU</b> — текущие частоты ядер через {@link CPUStatus}, плюс
 *       загрузка процесса из {@code /proc/self/stat}.</li>
 *   <li><b>Температура</b> — {@link CPUStatus#getTemperature()}: ядро уже
 *       сканирует {@code /sys/class/thermal}, дублировать этот обход не нужно.</li>
 * </ul>
 *
 * <p>Класс не держит ссылок на Activity и не создаёт потоков: тикер
 * запускает владелец HUD, а измерение занимает микросекунды.
 */
public final class PerfSampler {

    private static final String TAG = "Engine";
    private static final int HISTORY = 60;

    private final Context context;
    private final ActivityManager activityManager;

    // FPS-счётчик: кадры приходят извне, раз в окно превращаются в число.
    private long frameCount;
    private long windowStartMs;
    private float fps;
    private float frameTimeMs;

    private final float[] fpsHistory = new float[HISTORY];
    private int historyIndex;

    private long lastCpuTicks;
    private long lastCpuSampleMs;
    private float cpuLoadPercent;

    private int lastTemperature;
    private float ramUsedMb;
    private float ramTotalMb;

    public PerfSampler(Context context) {
        this.context = context.getApplicationContext();
        this.activityManager = (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE);
        this.windowStartMs = System.currentTimeMillis();
    }

    /** Вызывается рендерером на каждый отрисованный кадр. */
    public void onFrame() {
        frameCount++;
    }

    /**
     * Обновить метрики. Вызывать не чаще раза в {@code sampleMs}.
     *
     * @return снимок показателей
     */
    public Snapshot sample() {
        long now = System.currentTimeMillis();
        updateFps(now);
        updateRam();
        updateCpu(now);
        updateTemperature();
        return snapshot();
    }

    private void updateFps(long now) {
        long elapsed = now - windowStartMs;
        if (elapsed < 250) return;

        fps = frameCount * 1000f / elapsed;
        frameTimeMs = fps > 0 ? 1000f / fps : 0f;
        frameCount = 0;
        windowStartMs = now;

        fpsHistory[historyIndex] = fps;
        historyIndex = (historyIndex + 1) % HISTORY;
    }

    private void updateRam() {
        try {
            if (activityManager != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(info);
                ramTotalMb = info.totalMem / (1024f * 1024f);
                ramUsedMb = (info.totalMem - info.availMem) / (1024f * 1024f);
            }
        } catch (Throwable t) {
            P3Log.warn(TAG, "Не удалось прочитать память: " + t.getMessage());
        }
    }

    private void updateCpu(long now) {
        try {
            long ticks = readProcessTicks();
            if (lastCpuTicks > 0 && now > lastCpuSampleMs) {
                long deltaTicks = ticks - lastCpuTicks;
                long deltaMs = now - lastCpuSampleMs;
                if (deltaMs > 0) {
                    // CLK_TCK на Android = 100, т.е. 1 тик = 10 мс процессорного времени.
                    float cpuMs = deltaTicks * 10f;
                    int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
                    cpuLoadPercent = Math.min(100f, cpuMs / (deltaMs * (float) cores) * 100f);
                }
            }
            lastCpuTicks = ticks;
            lastCpuSampleMs = now;
        } catch (Throwable ignored) {
            // /proc может быть недоступен — не критично.
        }
    }

    private long readProcessTicks() {
        try (RandomAccessFile reader = new RandomAccessFile("/proc/self/stat", "r")) {
            String line = reader.readLine();
            if (line == null) return 0;
            // Поля после имени процесса (в скобках) — начиная с 14-го: utime, stime.
            int close = line.lastIndexOf(')');
            if (close < 0) return 0;
            String[] parts = line.substring(close + 2).split(" ");
            if (parts.length < 13) return 0;
            return Long.parseLong(parts[11]) + Long.parseLong(parts[12]);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void updateTemperature() {
        try {
            lastTemperature = CPUStatus.getTemperature();
        } catch (Throwable ignored) {
        }
    }

    /** Значения, которые показывает HUD. */
    public Snapshot snapshot() {
        short[] clocks = new short[0];
        try {
            clocks = CPUStatus.getCurrentClockSpeeds();
        } catch (Throwable ignored) {
        }
        return new Snapshot(fps, frameTimeMs, ramUsedMb, ramTotalMb, cpuLoadPercent,
                lastTemperature, clocks);
    }

    /** Пиковая частота любого ядра — удобно для строки «CPU». */
    public static short maxClock(short[] clocks) {
        if (clocks == null) return 0;
        short max = 0;
        for (short clock : clocks) {
            if (clock > max) max = clock;
        }
        return max;
    }

    /** История FPS для графика в HUD. */
    public float[] history() {
        float[] copy = new float[HISTORY];
        for (int i = 0; i < HISTORY; i++) copy[i] = fpsHistory[(historyIndex + i) % HISTORY];
        return copy;
    }

    /** Один кадр метрик. */
    public static final class Snapshot {
        public final float fps;
        public final float frameTimeMs;
        public final float ramUsedMb;
        public final float ramTotalMb;
        public final float cpuPercent;
        public final int temperature;
        public final short[] cpuClocks;

        Snapshot(float fps, float frameTimeMs, float ramUsedMb, float ramTotalMb,
                 float cpuPercent, int temperature, short[] cpuClocks) {
            this.fps = fps;
            this.frameTimeMs = frameTimeMs;
            this.ramUsedMb = ramUsedMb;
            this.ramTotalMb = ramTotalMb;
            this.cpuPercent = cpuPercent;
            this.temperature = temperature;
            this.cpuClocks = cpuClocks;
        }
    }
}
