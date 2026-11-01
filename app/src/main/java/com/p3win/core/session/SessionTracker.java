package com.p3win.core.session;

import android.app.Activity;

import com.p3win.core.log.P3Log;
import com.p3win.core.paths.AppPaths;
import com.p3win.data.P3Database;
import com.p3win.data.dao.SessionDao;
import com.p3win.data.entity.GameSession;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Учёт игровых сессий.
 *
 * <p>Работает целиком на lifecycle-колбэках приложения: ядро о P3WIN не знает,
 * поэтому {@code XServerDisplayActivity} не правится. Это ключевое решение
 * Фазы 4 — перехват происходит «снаружи» ядра.
 *
 * <p>Что пишется в БД:
 * <ul>
 *   <li>начало сессии (Activity создана);</li>
 *   <li>активное время на экране (сумма интервалов resume→pause);</li>
 *   <li>завершение и итоговая длительность;</li>
 *   <li>флаг «сессия прервана» — уничтожение без pause, типичный SIGSEGV.</li>
 * </ul>
 */
public final class SessionTracker {

    private static final String TAG = "Session";
    private static final SessionTracker INSTANCE = new SessionTracker();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private long currentSessionId = -1;
    private long startedAt;
    private long activeAccumMs;
    private long lastResumeAt;
    private boolean paused = true;
    private boolean crashed;
    private int containerId;
    private long gameId;
    private String executable = "";
    private String pendingFailureReason = "";

    private SessionTracker() {
    }

    public static SessionTracker get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void onSessionActivityCreated(Activity activity, int containerId, String shortcutPath) {
        onSessionActivityCreated(activity, containerId, shortcutPath, "");
    }

    /**
     * @param profileSnapshot условия запуска одной строкой: драйвер, DXVK,
     *                        Box64-пресет, разрешение, безопасный режим.
     *                        Хранится в сессии, чтобы через месяц можно было
     *                        понять, «на чём именно» упала игра.
     */
    public void onSessionActivityCreated(Activity activity, int containerId, String shortcutPath,
                                         String profileSnapshot) {
        closeStaleSession();
        this.containerId = containerId;
        this.executable = shortcutPath != null ? shortcutPath : "";
        this.gameId = resolveGameId(shortcutPath);
        begin(activity, profileSnapshot);
    }

    public void onSessionActivityStarted(Activity activity) {
        if (currentSessionId <= 0) begin(activity);
    }

    public void onSessionActivityResumed(Activity activity) {
        if (currentSessionId <= 0) return;
        paused = false;
        lastResumeAt = System.currentTimeMillis();
        if (startedAt == 0) startedAt = lastResumeAt;
    }

    public void onSessionActivityPaused(Activity activity) {
        if (currentSessionId <= 0 || paused) return;
        paused = true;
        if (lastResumeAt > 0) activeAccumMs += System.currentTimeMillis() - lastResumeAt;
        lastResumeAt = 0;
        flushProgress();
    }

    /**
     * Переключение в PiP не завершает сессию — поэтому onStop намеренно пуст.
     */
    public void onSessionActivityStopped(Activity activity) {
    }

    public void onSessionActivityDestroyed(Activity activity) {
        if (currentSessionId <= 0) return;
        if (!paused) {
            // Уничтожение без pause — почти всегда аварийное завершение.
            crashed = true;
            if (lastResumeAt > 0) activeAccumMs += System.currentTimeMillis() - lastResumeAt;
            lastResumeAt = 0;
        }
        finish(activity);
    }

    // ------------------------------------------------------------------
    // Логика
    // ------------------------------------------------------------------

    private void begin(Activity activity) {
        begin(activity, "");
    }

    private void begin(Activity activity, final String profileSnapshot) {
        startedAt = System.currentTimeMillis();
        activeAccumMs = 0;
        lastResumeAt = startedAt;
        paused = false;
        crashed = false;
        currentSessionId = -1;

        final P3Database db = P3Database.get(activity.getApplicationContext());
        if (db == null) return;

        executor.execute(() -> {
            try {
                GameSession session = new GameSession();
                session.containerId = containerId;
                session.gameId = gameId;
                session.executable = executable;
                session.startedAt = startedAt;
                session.endedAt = 0;
                session.durationMs = 0;
                session.crashed = false;
                session.exitCode = 0;
                session.activeMs = 0;
                session.profileSnapshot = profileSnapshot != null ? profileSnapshot : "";
                long id = db.sessionDao().insert(session);
                currentSessionId = id;
                P3Log.info(TAG, "Сессия #" + id + " начата (container=" + containerId
                        + ", game=" + gameId + ")");
            } catch (Throwable t) {
                P3Log.error(TAG, "Не удалось начать сессию", t);
            }
        });
    }

    private void flushProgress() {
        final long id = currentSessionId;
        if (id <= 0) return;
        final long active = activeAccumMs;
        P3Database db = P3Database.get();
        if (db == null) return;
        executor.execute(() -> {
            try {
                db.sessionDao().updateActive(id, active, System.currentTimeMillis());
            } catch (Throwable ignored) {
                // Промежуточная запись не критична.
            }
        });
    }

    /**
     * Причина сбоя, которую нужно записать в текущую сессию.
     *
     * <p>Вызывается <b>до</b> {@link #onSessionActivityDestroyed}: разбор лога
     * идёт, пока файл ещё открыт, а запись в базу — уже из {@code finish()}.
     * Так в статистике остаётся не только «упало», но и «почему».
     */
    public void setPendingFailureReason(String reason) {
        pendingFailureReason = reason != null ? reason : "";
    }

    private void finish(Activity activity) {
        final long id = currentSessionId;
        final String reason = pendingFailureReason;
        pendingFailureReason = "";
        final long end = System.currentTimeMillis();
        final long duration = startedAt > 0 ? end - startedAt : 0;
        final long active = activeAccumMs;
        final boolean wasCrash = crashed;
        currentSessionId = -1;
        paused = true;

        P3Database db = P3Database.get(activity != null ? activity.getApplicationContext() : null);
        if (db == null) return;

        executor.execute(() -> {
            try {
                SessionDao dao = db.sessionDao();
                dao.finish(id, end, duration, active, wasCrash, 0);
                if (!reason.isEmpty()) dao.setFailureReason(id, reason);
                if (gameId > 0) db.gameDao().addPlaytime(gameId, active);
                if (containerId > 0) db.containerDao().addPlaytime(containerId, active);

                // Достижения пересчитываются здесь, а не на главной: веха
                // «10 часов в играх» становится правдой именно сейчас.
                MilestoneEngine.refresh(db.context());
            } catch (Throwable t) {
                P3Log.error(TAG, "Не удалось закрыть сессию " + id, t);
            }
        });

        if (wasCrash) {
            P3Log.warn(TAG, "Сессия завершилась без pause — вероятен нативный сбой "
                    + "(SIGSEGV/SIGILL/SIGBUS)");
        }
    }

    /** Закрыть «повисшую» сессию, если предыдущее Activity умерло не по-хорошему. */
    private void closeStaleSession() {
        if (currentSessionId <= 0) return;
        crashed = !paused;
        finish(null);
    }

    private long resolveGameId(String shortcutPath) {
        if (shortcutPath == null || shortcutPath.isEmpty()) return 0;
        P3Database db = P3Database.get();
        if (db == null) return 0;
        try {
            String name = new File(shortcutPath).getName();
            com.p3win.data.entity.Game game = db.gameDao().findByShortcut(name);
            return game != null ? game.id : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // Публичное состояние
    // ------------------------------------------------------------------

    public long sessionElapsedMs() {
        if (currentSessionId <= 0) return 0;
        long elapsed = activeAccumMs;
        if (!paused && lastResumeAt > 0) elapsed += System.currentTimeMillis() - lastResumeAt;
        return elapsed;
    }

    public boolean hasActiveSession() {
        return currentSessionId > 0;
    }

    public long currentSessionId() {
        return currentSessionId;
    }

    public File currentLogDir() {
        android.content.Context context = com.p3win.P3Application.context();
        return context != null ? AppPaths.of(context).getLogsDir() : null;
    }
}
