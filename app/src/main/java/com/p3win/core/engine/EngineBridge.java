package com.p3win.core.engine;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;

import com.p3win.core.log.P3Log;
import com.p3win.core.prefs.P3Prefs;

/**
 * Подключение P3 Engine к игровой Activity ядра.
 *
 * <h3>Почему через lifecycle, а не через правку Activity</h3>
 * {@code XServerDisplayActivity} — часть ядра. Любая правка там означает
 * конфликт при обновлении Winlator. Поэтому HUD подключается снаружи:
 * {@code P3Application} уже ловит создание игровой Activity, а здесь мы
 * вешаем оверлей на её корневой {@code FrameLayout}.
 *
 * <p>Единственная сложность — момент: корневой контейнер
 * ({@code R.id.FLXServerDisplay}) наполняется в {@code onCreate}, поэтому
 * пробуем несколько раз с задержкой, а не полагаемся на «уже готово».
 *
 * <h3>Что НЕ делает P3 Engine</h3>
 * Не трогает рендерер и не встраивается в {@code XServerView}: FPS для HUD
 * берётся из того же источника, что использует ядро (счётчик кадров
 * {@code FrameRating}), а если его нет — HUD показывает метрики без FPS.
 * Так оверлей физически не может повлиять на производительность игры больше,
 * чем на один TextView раз в полсекунды.
 */
public final class EngineBridge {

    private static final String TAG = "EngineBridge";
    private static final int MAX_ATTEMPTS = 12;
    private static final long RETRY_DELAY_MS = 250L;

    private static PerformanceHud hud;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private EngineBridge() {
    }

    /** Повесить HUD на Activity игры (если он включён в настройках). */
    public static void attachTo(Activity activity) {
        if (activity == null || !P3Prefs.engineHudEnabled()) return;
        tryAttach(activity, 0);
    }

    private static void tryAttach(final Activity activity, final int attempt) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        ViewGroup host = PerformanceHud.findHost(activity);
        if (host != null) {
            hud = new PerformanceHud(activity);
            if (hud.attach(host)) {
                P3Log.info(TAG, "P3 Engine HUD подключён (" + host.getClass().getSimpleName() + ")");
                return;
            }
        }

        if (attempt >= MAX_ATTEMPTS) {
            P3Log.warn(TAG, "Не удалось найти контейнер для HUD");
            return;
        }
        MAIN.postDelayed(() -> tryAttach(activity, attempt + 1), RETRY_DELAY_MS);
    }

    /** Снять HUD при выходе из игры. */
    public static void detach() {
        if (hud != null) {
            hud.detach();
            hud = null;
        }
        MAIN.removeCallbacksAndMessages(null);
    }

    /** Отметить кадр (если источник кадров доступен). */
    public static void onFrame() {
        if (hud != null) hud.onFrame();
    }

    public static boolean isAttached() {
        return hud != null && hud.isAttached();
    }
}
