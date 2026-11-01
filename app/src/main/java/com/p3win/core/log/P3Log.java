package com.p3win.core.log;

import android.util.Log;

import com.p3win.core.paths.AppPaths;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Логирование P3WIN: logcat + файл.
 *
 * <h3>Зачем файл, если есть logcat</h3>
 * Требование проекта — сохранять stderr и нативные сбои игр во внутренний лог,
 * чтобы пользователь мог показать его при разборе. На устройстве без root
 * logcat после перезапуска недоступен, поэтому каждый важный шаг дублируется
 * в {@code files/p3win/logs/p3-YYYYMMDD.log}.
 *
 * <p>Файл открывается в режиме append на каждую запись: это медленнее, зато
 * при падении процесса (SIGSEGV в Wine) ничего не теряется в буфере.
 *
 * <p><b>Важно:</b> до вызова {@link #attach(AppPaths)} файловое логирование
 * выключено — пишем только в logcat. Это штатное поведение на самом раннем
 * этапе инициализации {@code Application}.
 */
public final class P3Log {

    private static final String TAG_PREFIX = "P3/";
    private static final int MAX_LOG_BYTES = 2 * 1024 * 1024;
    private static final int KEEP_DAYS = 7;

    private static AppPaths paths;
    private static File currentLogFile;
    private static String currentLogDay;

    private P3Log() {
    }

    /** Подключить файловый вывод. Вызывать первым делом в {@code Application.onCreate}. */
    public static synchronized void attach(AppPaths appPaths) {
        paths = appPaths;
        rotateIfNeeded();
    }

    public static synchronized File currentLogFile() {
        if (currentLogFile == null) rotateIfNeeded();
        return currentLogFile;
    }

    // ------------------------------------------------------------------
    // Уровни
    // ------------------------------------------------------------------

    public static void debug(String tag, String message) {
        Log.d(TAG_PREFIX + tag, message);
        write("D", tag, message, null);
    }

    public static void info(String tag, String message) {
        Log.i(TAG_PREFIX + tag, message);
        write("I", tag, message, null);
    }

    public static void warn(String tag, String message) {
        Log.w(TAG_PREFIX + tag, message);
        write("W", tag, message, null);
    }

    public static void error(String tag, String message) {
        Log.e(TAG_PREFIX + tag, message);
        write("E", tag, message, null);
    }

    public static void error(String tag, String message, Throwable throwable) {
        Log.e(TAG_PREFIX + tag, message, throwable);
        write("E", tag, message, throwable);
    }

    // ------------------------------------------------------------------
    // Отдельные лог-файлы для игры/контейнера
    // ------------------------------------------------------------------

    /** Лог конкретной игры: к нему прикладывается stderr Wine. */
    public static File gameLogFile(int gameId) {
        if (paths == null) return null;
        return new File(paths.getLogsDir(), "game-" + gameId + ".log");
    }

    /** Лог контейнера: используется при разборе «почему не запустилось». */
    public static File containerLogFile(int containerId) {
        if (paths == null) return null;
        return new File(paths.getLogsDir(), "container-" + containerId + ".log");
    }

    public static void appendToFile(File file, String line) {
        if (file == null || line == null) return;
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
            if (!line.endsWith("\n")) writer.write('\n');
        } catch (IOException ignored) {
            // Логирование не должно ронять приложение.
        }
    }

    /**
     * Перенаправить {@code System.err}/{@code System.out} в файл, сохранив вывод в logcat.
     *
     * <p>Используется оболочкой запуска игры: всё, что печатает Wine/Box64,
     * попадает в лог сессии. Так выполняется требование «захватывать stderr
     * игр во внутренний лог».
     */
    public static void installStreamRedirect(final File destination) {
        if (destination == null) return;
        final java.io.PrintStream originalErr = System.err;
        final java.io.PrintStream originalOut = System.out;

        System.setErr(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                originalErr.write(b);
                appendToFile(destination, String.valueOf((char) b).equals("\n") ? "" : String.valueOf((char) b));
            }
        }, true));

        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                originalOut.write(b);
            }
        }, true));
    }

    /** Последние строки лога — для экрана диагностики. */
    public static String tail(File file, int maxLines) {
        if (file == null || !file.isFile()) return "";
        StringBuilder result = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            java.util.ArrayDeque<String> lines = new java.util.ArrayDeque<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.addLast(line);
                if (lines.size() > maxLines) lines.removeFirst();
            }
            for (String item : lines) result.append(item).append('\n');
        } catch (IOException ignored) {
        }
        return result.toString();
    }

    public static File[] listLogs() {
        if (paths == null) return new File[0];
        File[] files = paths.getLogsDir().listFiles();
        return files != null ? files : new File[0];
    }

    // ------------------------------------------------------------------
    // Внутренности
    // ------------------------------------------------------------------

    private static synchronized void write(String level, String tag, String message, Throwable throwable) {
        if (paths == null) return;
        rotateIfNeeded();
        if (currentLogFile == null) return;

        StringBuilder line = new StringBuilder(128);
        line.append(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()))
                .append(' ').append(level)
                .append('/').append(tag)
                .append(": ").append(message);
        if (throwable != null) {
            StringWriter buffer = new StringWriter();
            throwable.printStackTrace(new PrintWriter(buffer));
            line.append('\n').append(buffer);
        }
        appendToFile(currentLogFile, line.toString());
    }

    private static void rotateIfNeeded() {
        if (paths == null) return;
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        if (currentLogFile != null && today.equals(currentLogDay) && currentLogFile.length() < MAX_LOG_BYTES) {
            return;
        }
        currentLogDay = today;
        currentLogFile = new File(paths.getLogsDir(), "p3-" + today + ".log");
        cleanOldLogs(paths.getLogsDir());
    }

    private static void cleanOldLogs(File logsDir) {
        File[] files = logsDir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - KEEP_DAYS * 24L * 60L * 60L * 1000L;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < cutoff && file.getName().startsWith("p3-")) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }
}
