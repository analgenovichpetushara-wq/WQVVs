package com.p3win.core.log;

import android.app.Activity;

import com.winlator.core.Callback;
import com.winlator.core.ProcessHelper;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Захват stderr/stdout игрового процесса в файл P3WIN.
 *
 * <h3>Зачем это отдельный класс, а не перенаправление System.err</h3>
 * Ядро запускает Wine через {@link ProcessHelper}: при DEBUG_MODE оно
 * отбрасывает вывод в {@code /dev/null}, а в отладочном режиме прогоняет его
 * через список {@code debugCallbacks}. Перенаправлять {@code System.err}
 * бессмысленно — процесс пишет в свой собственный файловый дескриптор.
 * Единственный корректный путь — подписаться на этот список.
 *
 * <p>Побочный эффект важнее самого логирования: пока есть хотя бы один
 * подписчик, ядро <b>не</b> отправляет вывод в {@code /dev/null} и включает
 * поток чтения. То есть подписка P3 автоматически включает сбор лога,
 * не требуя править {@code ProcessHelper}.
 *
 * <h3>Ограничения</h3>
 * Лог ограничен по объёму ({@link #MAX_BYTES}): бесконечный вывод сломанной
 * игры не должен забить память устройства. После достижения предела запись
 * останавливается с одной отметкой в конце файла.
 */
public final class GameLogCapture {

    private static final String TAG = "GameLog";
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private static Callback<String> activeCallback;
    private static File activeFile;
    private static final AtomicBoolean truncated = new AtomicBoolean(false);

    private GameLogCapture() {
    }

    /**
     * Начать запись вывода игрового процесса.
     *
     * @param activity   игровая Activity (нужна для Application-контекста)
     * @param sessionKey имя файла без расширения: id игры или контейнера
     * @return файл лога (может быть null, если пути недоступны)
     */
    public static File start(Activity activity, String sessionKey) {
        stop();
        if (activity == null) return null;

        String name = "game-" + (sessionKey != null && !sessionKey.isEmpty() ? sessionKey : "unknown");
        File file = new File(P3Log.currentLogFile() != null
                ? P3Log.currentLogFile().getParentFile()
                : activity.getCacheDir(), name + ".log");

        final File logFile = file;
        P3Log.appendToFile(logFile, "\n=== P3WIN: старт перехвата вывода ===\n");

        Callback<String> callback = line -> {
            if (line == null) return;
            if (logFile.length() >= MAX_BYTES) {
                if (truncated.compareAndSet(false, true)) {
                    P3Log.appendToFile(logFile, "\n=== P3WIN: достигнут предел " + MAX_BYTES
                            + " байт, запись остановлена ===\n");
                }
                return;
            }
            P3Log.appendToFile(logFile, line);
        };

        try {
            ProcessHelper.addDebugCallback(callback);
            activeCallback = callback;
            activeFile = logFile;
            P3Log.info(TAG, "Перехват вывода включён: " + logFile.getAbsolutePath());
        } catch (Throwable t) {
            P3Log.error(TAG, "Не удалось подписаться на вывод процесса", t);
            return null;
        }
        return logFile;
    }

    /** Прекратить запись. Обязательно вызывать при выходе из игры. */
    public static void stop() {
        if (activeCallback == null) return;
        try {
            ProcessHelper.removeDebugCallback(activeCallback);
        } catch (Throwable ignored) {
        }
        P3Log.appendToFile(activeFile, "=== P3WIN: конец перехвата вывода ===\n");
        activeCallback = null;
        activeFile = null;
        truncated.set(false);
    }

    public static boolean isCapturing() {
        return activeCallback != null;
    }

    /** Последние строки лога игры — для экрана «почему закрылось». */
    public static String tail(int lines) {
        return P3Log.tail(activeFile, lines);
    }

    /**
     * Найти причину падения по тексту лога.
     *
     * <p>Не «угадывание»: набор сигналов и типовых сообщений Wine/Box64,
     * которые реально встречаются на Adreno + Snapdragon 6 Gen 4.
     */
    public static String diagnose(File logFile) {
        String text = P3Log.tail(logFile, 400);
        if (text.isEmpty()) return "";

        if (text.contains("SIGILL")) {
            return "SIGILL — попытка исполнения неподдерживаемой инструкции. "
                    + "На Snapdragon 6 Gen 4 нет AArch32: 32-битный код обязан идти "
                    + "через Box64 (WoW64). Проверьте, что игра запускается как x86/x64.";
        }
        if (text.contains("SIGSEGV")) {
            return "SIGSEGV — падение внутри игры или драйвера. Часто виноват "
                    + "GPU-драйвер: попробуйте откатить Turnip в GPU Manager.";
        }
        if (text.contains("SIGBUS")) {
            return "SIGBUS — ошибка доступа к памяти. Обычно нехватка RAM "
                    + "или повреждённые файлы игры.";
        }
        if (text.contains("vulkan") && (text.contains("dlopen")
                || text.contains("Failed to load") || text.contains("ERROR_INCOMPATIBLE_DRIVER"))) {
            return "Vulkan-драйвер не загрузился. Для Adreno 810 нужен Turnip ветки 25.x "
                    + "и новее — откройте GPU Manager и откатите/смените драйвер.";
        }
        if (text.contains("err:module:import_dll") || text.contains("Unhandled exception")) {
            return "Игра не нашла DLL или упала в Wine. Проверьте wincomponents "
                    + "в настройках контейнера (direct3d, vcrun2010…).";
        }
        if (text.contains("out of memory") || text.contains("OutOfMemory")) {
            return "Не хватило памяти. Уменьшите разрешение/текстуры или закройте "
                    + "фоновые приложения.";
        }
        return "";
    }
}
