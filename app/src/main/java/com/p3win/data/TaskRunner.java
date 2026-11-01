package com.p3win.data;

import android.os.Handler;
import android.os.Looper;

import com.p3win.core.log.P3Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Исполнитель фоновых задач P3WIN.
 *
 * <p>Три правила слоя данных:
 * <ol>
 *   <li>SQLite и файловые операции никогда не выполняются в UI-потоке;</li>
 *   <li>любая ошибка фоновой задачи попадает в лог и не роняет приложение;</li>
 *   <li>результат возвращается в main-thread; проверку живости экрана
 *       выполняет вызывающая сторона (см. {@code P3Fragment.post}).</li>
 * </ol>
 */
public final class TaskRunner {

    public interface Job<T> {
        T run() throws Exception;
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    public interface ErrorCallback {
        void onError(Throwable throwable);
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TaskRunner() {
    }

    public static <T> void run(Job<T> job, Callback<T> callback) {
        run(job, callback, null);
    }

    public static <T> void run(final Job<T> job, final Callback<T> callback, final ErrorCallback errorCallback) {
        IO.execute(() -> {
            try {
                final T result = job.run();
                if (callback != null) MAIN.post(() -> callback.onResult(result));
            } catch (Throwable t) {
                P3Log.error("Task", "Фоновая задача завершилась ошибкой", t);
                if (errorCallback != null) MAIN.post(() -> errorCallback.onError(t));
            }
        });
    }

    public static void runVoid(final Runnable runnable) {
        IO.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                P3Log.error("Task", "Фоновая задача завершилась ошибкой", t);
            }
        });
    }

    public static void onMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
        else MAIN.post(runnable);
    }

    public static ExecutorService executor() {
        return IO;
    }
}
