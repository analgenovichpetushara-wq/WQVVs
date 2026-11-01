package com.p3win;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import com.p3win.core.log.CrashGuard;
import com.p3win.core.log.P3Log;
import com.p3win.core.paths.AppPaths;
import com.p3win.core.prefs.P3Prefs;
import com.p3win.core.session.SessionTracker;
import com.p3win.data.P3Repository;

/**
 * Точка входа P3WIN.
 *
 * <p>Здесь и только здесь выполняется проводка слоёв:
 * <pre>
 *   AppPaths → P3Log → CrashGuard → P3Prefs → P3Database → UI
 * </pre>
 *
 * <p>Ядро ({@code com.winlator.*}) о P3WIN не знает: зависимость строго
 * односторонняя {@code com.p3win.* → com.winlator.*}. Поэтому приложение
 * может запускаться на обновлённом ядре без переписывания оболочки.
 *
 * <p>Обязанности:
 * <ol>
 *   <li>Fail-Safe: перехват исключений в файл, без системного crash-диалога;</li>
 *   <li>учёт сессий контейнеров через lifecycle-колбэки;</li>
 *   <li>диагностика путей: сверить фактический data-dir с тем, на который
 *       рассчитаны rootfs и JNI.</li>
 * </ol>
 */
public class P3Application extends Application {

    private static final String TAG = "App";

    /**
     * Префикс data-dir, на который рассчитаны артефакты ядра:
     * ld.so.cache, libc, wineserver, ICD-описания Vulkan и JNI-макросы
     * ({@code APP_CACHE_DIR}, {@code VORTEK_SERVER_PATH}, {@code X11_SERVER_PATH}).
     *
     * <p>Этот путь не используется как рабочий — только для сверки на старте.
     * Если приложение переименовано, а артефакты нет, ошибка сразу попадает
     * в лог вместо «тихого» отказа контейнера через три минуты распаковки.
     */
    public static final String NATIVE_PREFIX = "/data/data/com.winlator";

    /** Имя игровой Activity ядра — точка перехвата сессий. */
    private static final String CORE_GAME_ACTIVITY = "com.winlator.XServerDisplayActivity";

    private static P3Application instance;
    private P3Repository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 1. Пути и логи — до первой записи в лог.
        AppPaths paths = AppPaths.of(this);
        P3Log.attach(paths);

        // 2. Fail-Safe.
        CrashGuard.install(this);

        // 3. Диагностика несовпадения путей.
        String mismatch = paths.checkNativePrefix(NATIVE_PREFIX);
        if (mismatch != null) {
            P3Log.error(TAG, mismatch);
        } else {
            P3Log.info(TAG, "Пути в порядке: " + paths.getDataDirPath());
        }

        // 4. Настройки и база.
        P3Prefs.init(this);
        try {
            repository = P3Repository.get(this);
            repository.open();
        } catch (Throwable t) {
            // База не должна ронять приложение: библиотека просто останется пустой.
            P3Log.error(TAG, "Не удалось открыть базу P3WIN", t);
        }

        // 5. Учёт сессий.
        registerActivityLifecycleCallbacks(new SessionTrackingCallbacks());

        P3Log.info(TAG, "P3WIN " + versionName() + " запущен на " + android.os.Build.MODEL
                + " (" + android.os.Build.VERSION.RELEASE + ")");
    }

    public static P3Application get() {
        return instance;
    }

    public static Context context() {
        return instance != null ? instance.getApplicationContext() : null;
    }

    public P3Repository repository() {
        return repository;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    /**
     * Автоматический учёт игровых сессий без правок в ядре.
     *
     * <p>{@code XServerDisplayActivity} и есть «сессия контейнера». Ловим её
     * старт и уничтожение. onStop не используем как финал: при переключении
     * в Picture-in-Picture он приходит, а игра продолжается.
     */
    private static final class SessionTrackingCallbacks implements ActivityLifecycleCallbacks {

        private static boolean isGameActivity(Activity activity) {
            return activity != null && CORE_GAME_ACTIVITY.equals(activity.getClass().getName());
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            if (!isGameActivity(activity)) return;
            int containerId = activity.getIntent() != null
                    ? activity.getIntent().getIntExtra("container_id", 0) : 0;
            String shortcutPath = activity.getIntent() != null
                    ? activity.getIntent().getStringExtra("shortcut_path") : null;
            SessionTracker.get().onSessionActivityCreated(activity, containerId, shortcutPath);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            if (isGameActivity(activity)) SessionTracker.get().onSessionActivityStarted(activity);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (isGameActivity(activity)) SessionTracker.get().onSessionActivityResumed(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (isGameActivity(activity)) SessionTracker.get().onSessionActivityPaused(activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (isGameActivity(activity)) SessionTracker.get().onSessionActivityStopped(activity);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            // не используется
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (isGameActivity(activity)) SessionTracker.get().onSessionActivityDestroyed(activity);
        }
    }
}
