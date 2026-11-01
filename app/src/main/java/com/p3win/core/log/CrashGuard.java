package com.p3win.core.log;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import com.p3win.core.paths.AppPaths;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Fail-Safe: перехват необработанных исключений уровня приложения.
 *
 * <h3>Что делает</h3>
 * <ol>
 *   <li>Пишет стектрейс в {@code files/p3win/logs/crash-*.log} <b>синхронно</b>
 *       (процесс могут убить сразу после вызова) и дублирует в logcat;</li>
 *   <li>Дополняет отчёт снимком устройства — модель, ABI, активный контейнер;</li>
 *   <li>Считает серии падений: три за минуту включают безопасный режим,
 *       чтобы следующий запуск прошёл без GPU-оверрайдов и пользователь
 *       смог вернуться в интерфейс и откатить драйвер;</li>
 *   <li>Распознаёт характерные для этого проекта сбои: SIGILL (попытка
 *       исполнения AArch32-кода на Cortex-A720/A520) и отказ dlopen Vulkan
 *       (несовместимый Turnip).</li>
 * </ol>
 *
 * <p>Перехватчик <b>не</b> пытается продолжить работу после исключения —
 * это небезопасно. Его задача: зафиксировать падение и подготовить
 * предсказуемое состояние следующего запуска.
 */
public final class CrashGuard implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "Crash";
    private static final long BURST_WINDOW_MS = 60_000L;
    private static final int BURST_THRESHOLD = 3;

    private static Thread.UncaughtExceptionHandler previousHandler;
    private static Context appContext;

    private CrashGuard() {
    }

    public static void install(Application application) {
        appContext = application.getApplicationContext();
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new CrashGuard());
        P3Log.info(TAG, "Fail-Safe активен");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            report(thread, throwable);
        } catch (Throwable ignored) {
            // Отчёт не должен мешать системному обработчику.
        }
        if (previousHandler != null) previousHandler.uncaughtException(thread, throwable);
    }

    private static void report(Thread thread, Throwable throwable) {
        if (appContext == null) return;
        AppPaths paths = AppPaths.of(appContext);

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File crashFile = new File(paths.getLogsDir(), "crash-" + stamp + ".log");

        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        printer.println("P3WIN crash report " + new Date());
        printer.println("device   : " + Build.MANUFACTURER + " " + Build.MODEL);
        printer.println("android  : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        printer.println("abi      : " + joinAbis());
        printer.println("package  : " + appContext.getPackageName());
        printer.println("thread   : " + thread.getName());
        printer.println();
        throwable.printStackTrace(printer);
        printer.flush();

        String text = writer.toString();
        try (java.io.FileWriter fileWriter = new java.io.FileWriter(crashFile, true)) {
            fileWriter.write(text);
        } catch (Exception ignored) {
        }

        android.util.Log.e("P3/" + TAG, text);
        trackBurst(paths, text);
    }

    private static String joinAbis() {
        StringBuilder builder = new StringBuilder();
        for (String abi : Build.SUPPORTED_ABIS) {
            if (builder.length() > 0) builder.append(',');
            builder.append(abi);
        }
        return builder.toString();
    }

    private static void trackBurst(AppPaths paths, String text) {
        File marker = new File(paths.getLogsDir(), ".crash-timestamps");
        long now = System.currentTimeMillis();
        StringBuilder kept = new StringBuilder();

        if (marker.isFile()) {
            for (String line : com.winlator.core.FileUtils.readLines(marker, true)) {
                try {
                    long value = Long.parseLong(line.trim());
                    if (now - value <= BURST_WINDOW_MS) kept.append(value).append('\n');
                } catch (NumberFormatException ignored) {
                }
            }
        }
        kept.append(now).append('\n');

        int burst = 0;
        for (String line : kept.toString().split("\n")) {
            if (!line.trim().isEmpty()) burst++;
        }

        com.winlator.core.FileUtils.writeString(marker, kept.toString());

        if (burst >= BURST_THRESHOLD) {
            com.p3win.core.prefs.P3Prefs.init(appContext);
            com.p3win.core.prefs.P3Prefs.setSafeMode(true);
            P3Log.warn(TAG, "Серия из " + burst + " падений → безопасный режим (GPU-оверрайды отключены)");
        }

        if (text.contains("SIGILL")) {
            P3Log.warn(TAG, "SIGILL: вероятно исполнение AArch32-кода. На Snapdragon 6 Gen 4 "
                    + "32-битный код должен идти только через Box64 (WoW64).");
        }
        if (text.contains("dlopen") && text.toLowerCase(Locale.US).contains("vulkan")) {
            P3Log.warn(TAG, "Отказ dlopen Vulkan: проверьте совместимость Turnip с Adreno 810 "
                    + "(GPU Manager → откат драйвера).");
        }
    }

    /**
     * Учесть <b>нативный</b> сбой игры.
     *
     * <p>Java-исключения сюда не попадают: Wine/Box64 падают в нативном коде,
     * и для Android это просто «процесс умер». Единственный признак — хвост
     * stderr, который разбирает {@link GameLogCapture}. Считаем такие сбои
     * вместе с Java-падениями: пользователю всё равно, где именно упало,
     * важно, что после трёх подряд приложение должно запуститься в
     * безопасном режиме.
     */
    public static void noteNativeCrash(Context context, String reason) {
        if (context == null) return;
        AppPaths paths = AppPaths.of(context.getApplicationContext());

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File crashFile = new File(paths.getLogsDir(), "native-" + stamp + ".log");

        String text = "P3WIN native crash report " + new Date() + "\n"
                + "device   : " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "abi      : " + joinAbis() + "\n"
                + "reason   : " + (reason != null && !reason.isEmpty() ? reason : "не определена") + "\n";
        try (java.io.FileWriter writer = new java.io.FileWriter(crashFile, true)) {
            writer.write(text);
        } catch (Exception ignored) {
        }

        P3Log.warn(TAG, "Нативный сбой: " + (reason != null && !reason.isEmpty() ? reason : "не определена"));
        trackBurst(paths, text + "\n" + (reason != null ? reason : ""));
    }

    public static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /** Для экрана диагностики: искусственно записать отчёт без падения. */
    public static void simulateCrash(Throwable throwable) {
        report(Thread.currentThread(), throwable);
    }
}
