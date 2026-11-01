package com.p3win.core.engine;

import android.app.Activity;

import com.p3win.core.log.P3Log;
import com.winlator.XServerDisplayActivity;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

/**
 * Источник кадров для P3 Engine.
 *
 * <h3>Где взять настоящий FPS</h3>
 * FPS нельзя «измерить» снаружи рендерера: единственный честный источник —
 * момент, когда ядро обновляет содержимое окна игры.
 * {@code WindowManager} даёт ровно такой колбэк
 * ({@code onUpdateWindowContent}), и штатный {@code FrameRating} считает FPS
 * именно по нему. Значит P3 Engine подписывается на тот же поток событий —
 * цифры совпадают с HUD ядра, а не «примерно похожи».
 *
 * <h3>Почему не через правку Activity</h3>
 * Мы получаем {@link XServer} через публичный {@code getXServer()} игровой
 * Activity — это часть её контракта, а не внутренности. Ни одна строка ядра
 * не меняется: при обновлении Winlator связка продолжит работать, пока
 * существует этот геттер (он используется и самим ядром).
 */
public final class FrameSource {

    private static final String TAG = "FrameSource";

    private FrameSource() {
    }

    /**
     * Подписать HUD на обновления содержимого окна.
     *
     * @return true, если подписка удалась
     */
    public static boolean attach(Activity activity) {
        if (!(activity instanceof XServerDisplayActivity)) return false;
        try {
            XServerDisplayActivity game = (XServerDisplayActivity) activity;
            XServer xServer = game.getXServer();
            if (xServer == null || xServer.windowManager == null) return false;

            xServer.windowManager.addOnWindowModificationListener(
                    new WindowManager.OnWindowModificationListener() {
                        @Override
                        public void onUpdateWindowContent(Window window) {
                            // Считаем только контент окна игры; служебные окна
                            // XServer (курсор, рамки) кадры не дают.
                            if (window != null && window.isRenderable()) {
                                EngineBridge.onFrame();
                            }
                        }
                    });
            P3Log.info(TAG, "Подписка на кадры ядра установлена");
            return true;
        } catch (Throwable t) {
            P3Log.warn(TAG, "Не удалось подписаться на кадры: " + t.getMessage());
            return false;
        }
    }
}
